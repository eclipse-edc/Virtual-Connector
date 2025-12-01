/*
 *  Copyright (c) 2025 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

package org.eclipse.edc.test.e2e.managementapi.v4;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.annotations.PostgresqlIntegrationTest;
import org.eclipse.edc.junit.extensions.ComponentRuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.spi.types.domain.callback.CallbackAddress;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndExtension;
import org.eclipse.edc.test.e2e.managementapi.ManagementEndToEndTestContext;
import org.eclipse.edc.test.e2e.managementapi.Runtimes;
import org.eclipse.edc.virtualized.nats.testfixtures.NatsEndToEndExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.http.ContentType.JSON;
import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.FINALIZED;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.PARTICIPANT_CONTEXT_ID;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.createParticipant;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.jsonLdContextArray;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.participantContext;
import static org.eclipse.edc.virtualized.test.system.fixtures.DockerImages.createPgContainer;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class ContractAgreementApiV4EndToEndTest {

    abstract static class Tests {

        @Order(0)
        @RegisterExtension
        static WireMockExtension mockJwksServer = WireMockExtension.newInstance()
                .options(wireMockConfig().dynamicPort())
                .build();

        private String participantTokenJwt;
        private ECKey oauthServerSigningKey;


        @BeforeEach
        void setup(ManagementEndToEndTestContext context, ParticipantContextService participantContextService) throws JOSEException {
            createParticipant(participantContextService, PARTICIPANT_CONTEXT_ID);

            // stub JWKS endpoint at /jwks/ returning 200 OK with a simple JWKS
            oauthServerSigningKey = new ECKeyGenerator(Curve.P_256).keyID(UUID.randomUUID().toString()).generate();
            participantTokenJwt = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey);

            // create JWKS with the participant's key
            var jwks = createObjectBuilder()
                    .add("keys", createArrayBuilder().add(createObjectBuilder(oauthServerSigningKey.toPublicJWK().toJSONObject())))
                    .build()
                    .toString();

            // use wiremock to host a JWKS endpoint
            mockJwksServer.stubFor(any(urlPathEqualTo("/.well-known/jwks"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(jwks)));
        }

        @AfterEach
        void teardown(ParticipantContextService participantContextService, ContractNegotiationStore store) {
            participantContextService.deleteParticipantContext(PARTICIPANT_CONTEXT_ID)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));

            store.queryNegotiations(QuerySpec.max()).forEach(cn -> store.deleteById(cn.getId()));
        }


        @Test
        void getById(ManagementEndToEndTestContext context, ContractNegotiationStore store) {
            var agreement = createContractAgreement("agreement-id");
            store.save(createContractNegotiationBuilder("cn1").contractAgreement(agreement).build());

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractagreements/agreement-id")
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .body(CONTEXT, contains(jsonLdContextArray()))
                    .body(TYPE, equalTo("ContractAgreement"))
                    .body(ID, is(agreement.getId()))
                    .body("agreementId", is(agreement.getAgreementId()))
                    .body("assetId", notNullValue())
                    .body("policy.assignee", is(agreement.getPolicy().getAssignee()))
                    .body("policy.assigner", is(agreement.getPolicy().getAssigner()));

        }

        @Test
        void getById_tokenBearerDoesNotOwnResource(ManagementEndToEndTestContext context, ContractNegotiationStore store, ParticipantContextService srv) {
            var agreement = createContractAgreement("agreement-id");
            store.save(createContractNegotiationBuilder("cn1").contractAgreement(agreement).build());

            var otherParticipantId = UUID.randomUUID().toString();
            createParticipant(srv, otherParticipantId);

            var token = context.createToken(otherParticipantId, oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractagreements/agreement-id")
                    .then()
                    .statusCode(403);
        }

        @Test
        void getById_tokenLacksRequiredScope(ManagementEndToEndTestContext context, ContractNegotiationStore store) {
            var agreement = createContractAgreement("agreement-id");
            store.save(createContractNegotiationBuilder("cn1").contractAgreement(agreement).build());

            var token = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:missing"));

            context.baseRequest(token)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractagreements/agreement-id")
                    .then()
                    .statusCode(403);
        }


        @Test
        void getNegotiationByAgreementId(ManagementEndToEndTestContext context, ContractNegotiationStore store) {
            store.save(createContractNegotiationBuilder("negotiation-id")
                    .contractAgreement(createContractAgreement("agreement-id"))
                    .build());

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractagreements/agreement-id/negotiation")
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .body(CONTEXT, contains(jsonLdContextArray()))
                    .body(TYPE, equalTo("ContractNegotiation"))
                    .body(ID, is("negotiation-id"));

        }

        @Test
        void getNegotiationByAgreementId_tokenBearerDoesNotOwnResource(ManagementEndToEndTestContext context, ContractNegotiationStore store, ParticipantContextService srv) {
            var agreement = createContractAgreement("agreement-id");
            store.save(createContractNegotiationBuilder("cn1").contractAgreement(agreement).build());

            var otherParticipantId = UUID.randomUUID().toString();
            createParticipant(srv, otherParticipantId);

            var token = context.createToken(otherParticipantId, oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractagreements/agreement-id/negotiation")
                    .then()
                    .statusCode(403);
        }

        @Test
        void getNegotiationByAgreementId_tokenLacksRequiredScope(ManagementEndToEndTestContext context, ContractNegotiationStore store) {
            var agreement = createContractAgreement("agreement-id");
            store.save(createContractNegotiationBuilder("cn1").contractAgreement(agreement).build());

            var token = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:missing"));

            context.baseRequest(token)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractagreements/agreement-id/negotiation")
                    .then()
                    .statusCode(403);
        }

        @Test
        void query(ManagementEndToEndTestContext context, ContractNegotiationStore store) {
            store.save(createContractNegotiationBuilder("cn1").contractAgreement(createContractAgreement("cn1")).build());
            store.save(createContractNegotiationBuilder("cn2").contractAgreement(createContractAgreement("cn2")).build());

            var jsonPath = context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractagreements/request")
                    .then()
                    .log().ifError()
                    .statusCode(200)
                    .contentType(JSON)
                    .body("size()", is(2))
                    .extract().jsonPath();

            assertThat(jsonPath.getString("[0].assetId")).isNotNull();
            assertThat(jsonPath.getString("[1].assetId")).isNotNull();
            assertThat(jsonPath.getString("[0].@id")).isIn("cn1", "cn2");
            assertThat(jsonPath.getString("[1].@id")).isIn("cn1", "cn2");
            assertThat(jsonPath.getList("[0].@context")).contains(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2);
            assertThat(jsonPath.getList("[1].@context")).contains(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2);
        }

        @Test
        void query_tokenBearerIsAdmin_shouldReturnAll(ManagementEndToEndTestContext context, ContractNegotiationStore store) {
            store.save(createContractNegotiationBuilder("cn1").contractAgreement(createContractAgreement("cn1")).build());
            store.save(createContractNegotiationBuilder("cn2").contractAgreement(createContractAgreement("cn2")).build());

            var token = context.createAdminToken(oauthServerSigningKey);

            var jsonPath = context.baseRequest(token)
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractagreements/request")
                    .then()
                    .log().ifError()
                    .statusCode(200)
                    .contentType(JSON)
                    .body("size()", is(2))
                    .extract().jsonPath();

            assertThat(jsonPath.getString("[0].assetId")).isNotNull();
            assertThat(jsonPath.getString("[1].assetId")).isNotNull();
            assertThat(jsonPath.getString("[0].@id")).isIn("cn1", "cn2");
            assertThat(jsonPath.getString("[1].@id")).isIn("cn1", "cn2");
            assertThat(jsonPath.getList("[0].@context")).contains(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2);
            assertThat(jsonPath.getList("[1].@context")).contains(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2);
        }

        @Test
        void query_shouldLimitToResourceOwner(ManagementEndToEndTestContext context, ContractNegotiationStore store) {
            var otherParticipantId = UUID.randomUUID().toString();

            store.save(createContractNegotiationBuilder("cn1").contractAgreement(createContractAgreement("cn1")).build());
            store.save(createContractNegotiationBuilder("cn2")
                    .participantContextId(otherParticipantId)
                    .contractAgreement(createContractAgreementBuilder("cn2").participantContextId(otherParticipantId).build()).build());

            var token = context.createAdminToken(oauthServerSigningKey);

            var jsonPath = context.baseRequest(token)
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractagreements/request")
                    .then()
                    .log().ifError()
                    .statusCode(200)
                    .contentType(JSON)
                    .body("size()", is(1))
                    .extract().jsonPath();

            assertThat(jsonPath.getString("[0].assetId")).isNotNull();
            assertThat(jsonPath.getString("[0].@id")).isEqualTo("cn1");
            assertThat(jsonPath.getList("[0].@context")).contains(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2);
        }

        @Test
        void query_tokenBearerNotEqualResourceOwner(ManagementEndToEndTestContext context, ContractNegotiationStore store, ParticipantContextService srv) {
            var otherParticipantId = UUID.randomUUID().toString();
            srv.createParticipantContext(participantContext(otherParticipantId))
                    .orElseThrow(f -> new AssertionError("ParticipantContext " + otherParticipantId + " not created."));

            store.save(createContractNegotiationBuilder("cn1").contractAgreement(createContractAgreement("cn1")).build());

            var token = context.createToken(otherParticipantId, oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractagreements/request")
                    .then()
                    .log().ifValidationFails()
                    .statusCode(403)
                    .body(containsString("User '%s' is not authorized to access this resource.".formatted(otherParticipantId)));
        }

        private ContractNegotiation.Builder createContractNegotiationBuilder(String negotiationId) {
            return ContractNegotiation.Builder.newInstance()
                    .id(negotiationId)
                    .counterPartyId(UUID.randomUUID().toString())
                    .counterPartyAddress("address")
                    .callbackAddresses(List.of(CallbackAddress.Builder.newInstance()
                            .uri("local://test")
                            .events(Set.of("test-event1", "test-event2"))
                            .build()))
                    .protocol("dataspace-protocol-http")
                    .contractOffer(contractOfferBuilder().build())
                    .participantContextId(PARTICIPANT_CONTEXT_ID)
                    .state(FINALIZED.code());
        }

        private ContractOffer.Builder contractOfferBuilder() {
            return ContractOffer.Builder.newInstance()
                    .id("test-offer-id")
                    .assetId("test-asset-id")
                    .policy(Policy.Builder.newInstance().build());
        }

        private ContractAgreement createContractAgreement(String id) {
            return createContractAgreementBuilder(id)
                    .build();
        }

        private ContractAgreement.Builder createContractAgreementBuilder(String id) {
            return ContractAgreement.Builder.newInstance()
                    .id(id)
                    .assetId(UUID.randomUUID().toString())
                    .consumerId(UUID.randomUUID() + "-consumer")
                    .providerId(UUID.randomUUID() + "-provider")
                    .policy(Policy.Builder.newInstance().assignee("assignee").assigner("assigner").build())
                    .participantContextId(PARTICIPANT_CONTEXT_ID);
        }
    }

    @Nested
    @EndToEndTest
    class InMemory extends Tests {

        @RegisterExtension
        static RuntimeExtension runtime = ComponentRuntimeExtension.Builder.newInstance()
                .name(Runtimes.ControlPlane.NAME)
                .modules(Runtimes.ControlPlane.MODULES)
                .endpoints(Runtimes.ControlPlane.ENDPOINTS.build())
                .configurationProvider(Runtimes.ControlPlane::config)
                .paramProvider(ManagementEndToEndTestContext.class, ManagementEndToEndTestContext::forContext)
                .configurationProvider(() -> ConfigFactory.fromMap(Map.of("edc.iam.oauth2.jwks.url", "http://localhost:" + mockJwksServer.getPort() + "/.well-known/jwks")))
                .build();
    }

    @Nested
    @PostgresqlIntegrationTest
    class Postgres extends Tests {

        @RegisterExtension
        @Order(0)
        static final PostgresqlEndToEndExtension POSTGRES_EXTENSION = new PostgresqlEndToEndExtension(createPgContainer());

        @Order(0)
        @RegisterExtension
        static final NatsEndToEndExtension NATS_EXTENSION = new NatsEndToEndExtension();

        @Order(1)
        @RegisterExtension
        static final BeforeAllCallback SETUP = context -> {
            POSTGRES_EXTENSION.createDatabase(Runtimes.ControlPlane.NAME.toLowerCase());
            NATS_EXTENSION.createStream("state_machine", "negotiations.>", "transfers.>");
            NATS_EXTENSION.createConsumer("state_machine", "cn-subscriber", "negotiations.>");
            NATS_EXTENSION.createConsumer("state_machine", "tp-subscriber", "transfers.>");
        };

        @Order(3)
        @RegisterExtension
        static final BeforeAllCallback SEED = context -> {
            POSTGRES_EXTENSION.execute(Runtimes.ControlPlane.NAME.toLowerCase(), "ALTER TABLE edc_contract_negotiation REPLICA IDENTITY FULL;");
            POSTGRES_EXTENSION.execute(Runtimes.ControlPlane.NAME.toLowerCase(), "ALTER TABLE edc_transfer_process REPLICA IDENTITY FULL;");
        };

        @Order(2)
        @RegisterExtension
        static RuntimeExtension runtime = ComponentRuntimeExtension.Builder.newInstance()
                .name(Runtimes.ControlPlane.NAME)
                .modules(Runtimes.ControlPlane.MODULES)
                .modules(Runtimes.ControlPlane.PG_MODULES)
                .endpoints(Runtimes.ControlPlane.ENDPOINTS.build())
                .configurationProvider(Runtimes.ControlPlane::config)
                .configurationProvider(() -> POSTGRES_EXTENSION.configFor(Runtimes.ControlPlane.NAME.toLowerCase()))
                .configurationProvider(Postgres::runtimeConfiguration)
                .configurationProvider(() -> ConfigFactory.fromMap(Map.of("edc.iam.oauth2.jwks.url", "http://localhost:" + mockJwksServer.getPort() + "/.well-known/jwks")))
                .paramProvider(ManagementEndToEndTestContext.class, ManagementEndToEndTestContext::forContext)
                .build();

        private static Config runtimeConfiguration() {
            return ConfigFactory.fromMap(new HashMap<>() {
                {
                    put("edc.postgres.cdc.url", POSTGRES_EXTENSION.getJdbcUrl(Runtimes.ControlPlane.NAME.toLowerCase()));
                    put("edc.postgres.cdc.user", POSTGRES_EXTENSION.getUsername());
                    put("edc.postgres.cdc.password", POSTGRES_EXTENSION.getPassword());
                    put("edc.postgres.cdc.slot", "edc_cdc_slot_" + Runtimes.ControlPlane.NAME.toLowerCase());
                    put("edc.nats.cn.subscriber.url", NATS_EXTENSION.getNatsUrl());
                    put("edc.nats.cn.publisher.url", NATS_EXTENSION.getNatsUrl());
                    put("edc.nats.tp.subscriber.url", NATS_EXTENSION.getNatsUrl());
                    put("edc.nats.tp.publisher.url", NATS_EXTENSION.getNatsUrl());
                }
            });
        }

        @AfterEach
        void cleanup() {
            POSTGRES_EXTENSION.execute(Runtimes.ControlPlane.NAME.toLowerCase(), "DELETE FROM edc_contract_negotiation;");
            POSTGRES_EXTENSION.execute(Runtimes.ControlPlane.NAME.toLowerCase(), "DELETE FROM edc_contract_agreement;");
        }
    }
}
