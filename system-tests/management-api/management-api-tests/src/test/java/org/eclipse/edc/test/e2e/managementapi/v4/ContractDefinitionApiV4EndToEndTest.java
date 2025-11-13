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
import jakarta.json.JsonArray;
import jakarta.json.JsonObjectBuilder;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.junit.annotations.ApiTest;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.annotations.PostgresqlIntegrationTest;
import org.eclipse.edc.junit.extensions.ComponentRuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContextState;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.http.ContentType.JSON;
import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.query.Criterion.criterion;
import static org.eclipse.edc.virtualized.test.system.fixtures.DockerImages.createPgContainer;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

@ApiTest
public class ContractDefinitionApiV4EndToEndTest {

    @SuppressWarnings("JUnitMalformedDeclaration")
    abstract static class Tests {
        private static final String PARTICIPANT_CONTEXT_ID = "test-participant";
        @Order(0)
        @RegisterExtension
        static WireMockExtension mockJwksServer = WireMockExtension.newInstance()
                .options(wireMockConfig().dynamicPort())
                .build();
        private String participantTokenJwt;
        private ECKey oauthServerSigningKey;

        @BeforeEach
        void setup(ManagementEndToEndTestContext context, ParticipantContextService participantContextService)
                throws JOSEException {
            createParticipant(participantContextService, PARTICIPANT_CONTEXT_ID);

            // stub JWKS endpoint at /jwks/ returning 200 OK with a simple JWKS
            oauthServerSigningKey = new ECKeyGenerator(Curve.P_256).keyID(UUID.randomUUID().toString())
                    .generate();
            participantTokenJwt = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey);

            // create JWKS with the participant's key
            var jwks = createObjectBuilder()
                    .add("keys", createArrayBuilder().add(createObjectBuilder(
                            oauthServerSigningKey.toPublicJWK().toJSONObject())))
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
        void teardown(ParticipantContextService participantContextService) {
            participantContextService.deleteParticipantContext(PARTICIPANT_CONTEXT_ID)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));
        }

        @Test
        void queryContractDefinitions_noQuerySpec(ManagementEndToEndTestContext context,
                                                  ContractDefinitionStore store) {
            var id = UUID.randomUUID().toString();
            store.save(createContractDefinition(id).build());

            var body = context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractdefinitions/request")
                    .then()
                    .statusCode(200)
                    .body("size()", greaterThan(0))
                    .extract().body().as(Map[].class);

            var assetsSelector = Arrays.stream(body)
                    .filter(it -> it.get(ID).equals(id))
                    .map(it -> it.get("assetsSelector"))
                    .findAny();

            assertThat(assetsSelector).isPresent().get().asInstanceOf(LIST).hasSize(2);
        }

        @Test
        void queryContractDefinitionWithSimplePrivateProperties(ManagementEndToEndTestContext context, ContractDefinitionStore store) {
            var id = UUID.randomUUID().toString();
            var requestJson = createDefinitionBuilder(id)
                    .add("privateProperties", createObjectBuilder()
                            .add("newKey", createObjectBuilder().add(ID, "newValue"))
                            .build())
                    .build()
                    .toString();

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .body(requestJson)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractdefinitions")
                    .then()
                    .statusCode(200)
                    .body("@id", equalTo(id));

            var matchingQuery = context.query(
                    criterion("id", "=", id),
                    criterion("privateProperties.'%snewKey'.@id".formatted(EDC_NAMESPACE), "=",
                            "newValue")).toString();

            context.baseRequest(participantTokenJwt)
                    .body(matchingQuery)
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractdefinitions/request")
                    .then()
                    .log().ifValidationFails()
                    .statusCode(200)
                    .body("size()", is(1));

            var nonMatchingQuery = context.query(
                    criterion("id", "=", id),
                    criterion("privateProperties.'%snewKey'.@id".formatted(EDC_NAMESPACE), "=",
                            "anything-else")).toString();

            context.baseRequest(participantTokenJwt)
                    .body(nonMatchingQuery)
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractdefinitions/request")
                    .then()
                    .log().ifError()
                    .statusCode(200)
                    .body("size()", is(0));
        }

        @Test
        void queryContractDefinitions_sortByCreatedDate(ManagementEndToEndTestContext context,
                                                        ContractDefinitionStore store) {
            var id1 = UUID.randomUUID().toString();
            var id2 = UUID.randomUUID().toString();
            var id3 = UUID.randomUUID().toString();
            var createdAtTime = new AtomicLong(1000L);
            Stream.of(id1, id2, id3).forEach(id -> store.save(createContractDefinition(id)
                    .createdAt(createdAtTime.getAndIncrement()).build()));

            var query = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "QuerySpec")
                    .add("sortField", "createdAt")
                    .add("sortOrder", "DESC")
                    .add("limit", 100)
                    .add("offset", 0)
                    .build()
                    .toString();

            var result = context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .body(query)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractdefinitions/request")
                    .then()
                    .log().ifError()
                    .statusCode(200)
                    .body("size()", is(3))
                    .extract()
                    .as(List.class);

            assertThat(result)
                    .extracting(cd -> ((LinkedHashMap<?, ?>) cd).get(ID))
                    .containsExactlyElementsOf(List.of(id3, id2, id1));
        }

        @Test
        void shouldCreateAndRetrieve(ManagementEndToEndTestContext context, ContractDefinitionStore store) {
            var id = UUID.randomUUID().toString();
            var requestJson = createDefinitionBuilder(id)
                    .build()
                    .toString();

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .body(requestJson)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractdefinitions")
                    .then()
                    .statusCode(200)
                    .body("@id", equalTo(id));

            var actual = store.findById(id);

            assertThat(actual.getId()).matches(id);
        }

        @Test
        void delete(ManagementEndToEndTestContext context, ContractDefinitionStore store) {
            var id = UUID.randomUUID().toString();
            var entity = createContractDefinition(id).build();
            store.save(entity);

            context.baseRequest(participantTokenJwt)
                    .delete("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractdefinitions/" + id)
                    .then()
                    .statusCode(204);

            var actual = store.findById(id);

            assertThat(actual).isNull();
        }

        @Test
        void update_whenExists(ManagementEndToEndTestContext context, ContractDefinitionStore store) {
            var id = UUID.randomUUID().toString();
            var entity = createContractDefinition(id).build();
            store.save(entity);

            var updated = createDefinitionBuilder(id)
                    .add("accessPolicyId", "new-policy")
                    .build()
                    .toString();

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .body(updated)
                    .put("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractdefinitions")
                    .then()
                    .statusCode(204);

            var actual = store.findById(id);

            assertThat(actual.getAccessPolicyId()).isEqualTo("new-policy");
        }

        @Test
        void update_whenNotExists(ManagementEndToEndTestContext context) {
            var updated = createDefinitionBuilder(UUID.randomUUID().toString())
                    .add("accessPolicyId", "new-policy")
                    .build()
                    .toString();

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .body(updated)
                    .put("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractdefinitions")
                    .then()
                    .statusCode(404);
        }

        private JsonObjectBuilder createDefinitionBuilder(String id) {
            return createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "ContractDefinition")
                    .add(ID, id)
                    .add("accessPolicyId", UUID.randomUUID().toString())
                    .add("contractPolicyId", UUID.randomUUID().toString())
                    .add("assetsSelector", createArrayBuilder()
                            .add(createCriterionBuilder("foo", "=", "bar"))
                            .add(createCriterionBuilder("bar", "=", "baz")).build());
        }

        private JsonObjectBuilder createCriterionBuilder(String left, String operator, String right) {
            return createObjectBuilder()
                    .add(TYPE, "Criterion")
                    .add("operandLeft", left)
                    .add("operator", operator)
                    .add("operandRight", right);
        }

        private ContractDefinition.Builder createContractDefinition(String id) {
            return ContractDefinition.Builder.newInstance()
                    .id(id)
                    .accessPolicyId(UUID.randomUUID().toString())
                    .contractPolicyId(UUID.randomUUID().toString())
                    .participantContextId(PARTICIPANT_CONTEXT_ID)
                    .assetsSelectorCriterion(criterion("foo", "=", "bar"))
                    .assetsSelectorCriterion(criterion("bar", "=", "baz"));
        }

        private JsonArray jsonLdContext() {
            return createArrayBuilder()
                    .add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2)
                    .build();
        }

        private void createParticipant(ParticipantContextService participantContextService,
                                       String participantContextId) {
            var pc = ParticipantContext.Builder.newInstance()
                    .participantContextId(participantContextId)
                    .state(ParticipantContextState.ACTIVATED)
                    .build();

            participantContextService.createParticipantContext(pc)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));
        }
    }

    @Nested
    @EndToEndTest
    class InMemory extends Tests {

        @Order(1)
        @RegisterExtension
        static RuntimeExtension runtime = ComponentRuntimeExtension.Builder.newInstance()
                .name(Runtimes.ControlPlane.NAME)
                .modules(Runtimes.ControlPlane.MODULES)
                .endpoints(Runtimes.ControlPlane.ENDPOINTS.build())
                .configurationProvider(Runtimes.ControlPlane::config)
                .configurationProvider(() -> ConfigFactory.fromMap(Map.of("edc.iam.oauth2.jwks.url",
                        "http://localhost:" + mockJwksServer.getPort() + "/.well-known/jwks")))
                .paramProvider(ManagementEndToEndTestContext.class,
                        ManagementEndToEndTestContext::forContext)
                .build();
    }

    @Nested
    @PostgresqlIntegrationTest
    class Postgres extends AssetApiV4EndToEndTest.Tests {

        @RegisterExtension
        @Order(0)
        static final PostgresqlEndToEndExtension POSTGRES_EXTENSION = new PostgresqlEndToEndExtension(
                createPgContainer());

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
            POSTGRES_EXTENSION.execute(Runtimes.ControlPlane.NAME.toLowerCase(),
                    "ALTER TABLE edc_contract_negotiation REPLICA IDENTITY FULL;");
            POSTGRES_EXTENSION.execute(Runtimes.ControlPlane.NAME.toLowerCase(),
                    "ALTER TABLE edc_transfer_process REPLICA IDENTITY FULL;");
        };
        @Order(2)
        @RegisterExtension
        static RuntimeExtension runtime = ComponentRuntimeExtension.Builder.newInstance()
                .name(Runtimes.ControlPlane.NAME)
                .modules(Runtimes.ControlPlane.MODULES)
                .modules(Runtimes.ControlPlane.PG_MODULES)
                .endpoints(Runtimes.ControlPlane.ENDPOINTS.build())
                .configurationProvider(Runtimes.ControlPlane::config)
                .configurationProvider(() -> POSTGRES_EXTENSION
                        .configFor(Runtimes.ControlPlane.NAME.toLowerCase()))
                .configurationProvider(
                        ContractDefinitionApiV4EndToEndTest.Postgres::runtimeConfiguration)
                .configurationProvider(() -> ConfigFactory.fromMap(Map.of("edc.iam.oauth2.jwks.url",
                        "http://localhost:" + mockJwksServer.getPort() + "/.well-known/jwks")))
                .paramProvider(ManagementEndToEndTestContext.class,
                        ManagementEndToEndTestContext::forContext)
                .build();

        private static Config runtimeConfiguration() {
            return ConfigFactory.fromMap(new HashMap<>() {
                {
                    put("edc.postgres.cdc.url", POSTGRES_EXTENSION
                            .getJdbcUrl(Runtimes.ControlPlane.NAME.toLowerCase()));
                    put("edc.postgres.cdc.user", POSTGRES_EXTENSION.getUsername());
                    put("edc.postgres.cdc.password", POSTGRES_EXTENSION.getPassword());
                    put("edc.postgres.cdc.slot",
                            "edc_cdc_slot_" + Runtimes.ControlPlane.NAME.toLowerCase());
                    put("edc.nats.cn.subscriber.url", NATS_EXTENSION.getNatsUrl());
                    put("edc.nats.cn.publisher.url", NATS_EXTENSION.getNatsUrl());
                    put("edc.nats.tp.subscriber.url", NATS_EXTENSION.getNatsUrl());
                    put("edc.nats.tp.publisher.url", NATS_EXTENSION.getNatsUrl());
                }
            });
        }

    }

}