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
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.annotations.PostgresqlIntegrationTest;
import org.eclipse.edc.junit.extensions.ComponentRuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyType;
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
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.AGREED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.REQUESTED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.TERMINATED;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.PARTICIPANT_CONTEXT_ID;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.createParticipant;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.jsonLdContext;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.jsonLdContextArray;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.participantContext;
import static org.eclipse.edc.virtualized.test.system.fixtures.DockerImages.createPgContainer;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesRegex;

public class ContractNegotiationApiV4EndToEndTest {

    abstract static class Tests {

        private static final String COUNTER_PARTY_ID = "counter-party-id";
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
        void initiate(ManagementEndToEndTestContext context, ContractNegotiationStore store) {

            var requestJson = contractRequestJson();

            var id = context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .body(requestJson.toString())
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations")
                    .then()
                    .log().ifValidationFails()
                    .statusCode(200)
                    .contentType(JSON)
                    .extract().jsonPath().getString(ID);

            assertThat(store.findById(id)).isNotNull();
        }

        @Test
        void initiate_whenValidationFails(ManagementEndToEndTestContext context, ContractNegotiationStore store) {

            var requestJson = createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(TYPE, "ContractRequest")
                    .add("counterPartyAddress", "test-address")
                    .build();

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .body(requestJson.toString())
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations")
                    .then()
                    .log().ifValidationFails()
                    .statusCode(400);
        }

        @Test
        void initiate_tokenBearerWrong(ManagementEndToEndTestContext context, ParticipantContextService service) {
            var requestJson = contractRequestJson();

            var otherParticipantId = UUID.randomUUID().toString();

            service.createParticipantContext(participantContext(otherParticipantId))
                    .orElseThrow(f -> new AssertionError("ParticipantContext " + otherParticipantId + " not created."));

            var token = context.createToken(otherParticipantId, oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .body(requestJson.toString())
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations")
                    .then()
                    .log().ifValidationFails()
                    .statusCode(403)
                    .body(containsString("User '%s' is not authorized to access this resource.".formatted(otherParticipantId)));
        }

        @Test
        void initiate_tokenLacksWriteScope(ManagementEndToEndTestContext context, ParticipantContextService service) {
            var requestJson = contractRequestJson();

            var otherParticipantId = UUID.randomUUID().toString();

            service.createParticipantContext(participantContext(otherParticipantId))
                    .orElseThrow(f -> new AssertionError("ParticipantContext " + otherParticipantId + " not created."));

            var token = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:read"));

            context.baseRequest(token)
                    .contentType(JSON)
                    .body(requestJson.toString())
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations")
                    .then()
                    .log().ifValidationFails()
                    .statusCode(403)
                    .body(matchesRegex("(?s).*Required scope.*missing.*"));
        }

        @Test
        void initiate_tokenBearerIsAdmin(ManagementEndToEndTestContext context, ContractNegotiationStore store) {

            var requestJson = contractRequestJson();

            var token = context.createAdminToken(oauthServerSigningKey);

            var id = context.baseRequest(token)
                    .contentType(JSON)
                    .body(requestJson.toString())
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations")
                    .then()
                    .log().ifValidationFails()
                    .statusCode(200)
                    .contentType(JSON)
                    .extract().jsonPath().getString(ID);

            assertThat(store.findById(id)).isNotNull();
        }

        @Test
        void getById(ManagementEndToEndTestContext context, ContractNegotiationStore store) {
            store.save(createContractNegotiationBuilder("cn1").contractAgreement(createContractAgreement("cn1")).build());

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1")
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .body(CONTEXT, contains(jsonLdContextArray()))
                    .body(TYPE, equalTo("ContractNegotiation"))
                    .body(ID, is("cn1"))
                    .body("protocol", equalTo("dataspace-protocol-http"));
        }

        @Test
        void getById_tokenBearerDoesNotOwnResource(ManagementEndToEndTestContext context, ContractNegotiationStore store, ParticipantContextService srv) {
            store.save(createContractNegotiationBuilder("cn1").contractAgreement(createContractAgreement("cn1")).build());

            var otherParticipantId = UUID.randomUUID().toString();
            createParticipant(srv, otherParticipantId);

            var token = context.createToken(otherParticipantId, oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1")
                    .then()
                    .statusCode(403);
        }

        @Test
        void getById_tokenLacksRequiredScope(ManagementEndToEndTestContext context, ContractNegotiationStore store) {
            store.save(createContractNegotiationBuilder("cn1").contractAgreement(createContractAgreement("cn1")).build());

            var token = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:missing"));

            context.baseRequest(token)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1")
                    .then()
                    .statusCode(403);
        }

        @Test
        void getState(ManagementEndToEndTestContext context, ContractNegotiationStore store) {
            var state = ContractNegotiationStates.FINALIZED.code(); // all other states could be modified by the state machine
            store.save(createContractNegotiationBuilder("cn1").state(state).build());

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1/state")
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .body("state", is("FINALIZED"));
        }

        @Test
        void getState_tokenBearerDoesNotOwnResource(ManagementEndToEndTestContext context, ContractNegotiationStore store, ParticipantContextService srv) {
            var state = ContractNegotiationStates.FINALIZED.code();
            store.save(createContractNegotiationBuilder("cn1").state(state).build());

            var otherParticipantId = UUID.randomUUID().toString();
            createParticipant(srv, otherParticipantId);

            var token = context.createToken(otherParticipantId, oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1/state")
                    .then()
                    .statusCode(403);

        }

        @Test
        void getState_tokenLacksRequiredScope(ManagementEndToEndTestContext context, ContractNegotiationStore store) {
            var state = ContractNegotiationStates.FINALIZED.code();
            store.save(createContractNegotiationBuilder("cn1").state(state).build());

            var token = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:missing"));

            context.baseRequest(token)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1/state")
                    .then()
                    .statusCode(403);

        }

        @Test
        void getAgreement(ManagementEndToEndTestContext context, ContractNegotiationStore store) {
            var agreement = createContractAgreement("cn1");
            store.save(createContractNegotiationBuilder("cn1").contractAgreement(agreement).build());

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1/agreement")
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .body(CONTEXT, contains(jsonLdContextArray()))
                    .body(TYPE, equalTo("ContractAgreement"))
                    .body(ID, is("cn1"))
                    .body("assetId", equalTo(agreement.getAssetId()))
                    .body("policy.'@type'", equalTo("Agreement"));

        }

        @Test
        void getAgreement_tokenBearerDoesNotOwnResource(ManagementEndToEndTestContext context,
                                                        ContractNegotiationStore store, ParticipantContextService srv) {
            var agreement = createContractAgreement("cn1");
            store.save(createContractNegotiationBuilder("cn1").contractAgreement(agreement).build());

            var otherParticipantId = UUID.randomUUID().toString();
            createParticipant(srv, otherParticipantId);

            var token = context.createToken(otherParticipantId, oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1/agreement")
                    .then()
                    .statusCode(403);

        }

        @Test
        void getAgreement_tokenLacksRequiredScope(ManagementEndToEndTestContext context,
                                                  ContractNegotiationStore store) {
            var agreement = createContractAgreement("cn1");
            store.save(createContractNegotiationBuilder("cn1").contractAgreement(agreement).build());

            var token = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:missing"));

            context.baseRequest(token)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1/agreement")
                    .then()
                    .statusCode(403);

        }

        @Test
        void query(ManagementEndToEndTestContext context, ContractNegotiationStore store) {
            var id1 = UUID.randomUUID().toString();
            var id2 = UUID.randomUUID().toString();
            store.save(createContractNegotiationBuilder(id1).counterPartyAddress(context.providerProtocolUrl(COUNTER_PARTY_ID)).build());
            store.save(createContractNegotiationBuilder(id2).counterPartyAddress(context.providerProtocolUrl(COUNTER_PARTY_ID)).build());

            var query = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "QuerySpec")
                    .add("filterExpression", createArrayBuilder()
                            .add(createObjectBuilder()
                                    .add(TYPE, "Criterion")
                                    .add("operandLeft", "id")
                                    .add("operator", "in")
                                    .add("operandRight", createArrayBuilder().add(id1).add(id2))
                            )
                            .add(createObjectBuilder()
                                    .add(TYPE, "Criterion")
                                    .add("operandLeft", "pending")
                                    .add("operator", "=")
                                    .add("operandRight", false)
                            )
                    )
                    .build();

            var jsonPath = context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .body(query.toString())
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/request")
                    .then()
                    .log().ifValidationFails()
                    .statusCode(200)
                    .contentType(JSON)
                    .body("size()", is(2))
                    .extract().jsonPath();

            assertThat(jsonPath.getString("[0].counterPartyAddress")).isEqualTo(context.providerProtocolUrl(COUNTER_PARTY_ID));
            assertThat(jsonPath.getString("[0].@id")).isIn(id1, id2);
            assertThat(jsonPath.getString("[1].@id")).isIn(id1, id2);
            assertThat(jsonPath.getString("[0].protocol")).isEqualTo("dataspace-protocol-http");
            assertThat(jsonPath.getString("[1].protocol")).isEqualTo("dataspace-protocol-http");
            assertThat(jsonPath.getString("[0].@type")).isEqualTo("ContractNegotiation");
            assertThat(jsonPath.getString("[1].@type")).isEqualTo("ContractNegotiation");
            assertThat(jsonPath.getList("[0].@context")).contains(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2);
            assertThat(jsonPath.getList("[1].@context")).contains(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2);

        }

        @Test
        void query_tokenBearerIsAdmin_shouldReturnAll(ManagementEndToEndTestContext context, ContractNegotiationStore store) {

            var id1 = UUID.randomUUID().toString();
            var id2 = UUID.randomUUID().toString();
            store.save(createContractNegotiationBuilder(id1).counterPartyAddress(context.providerProtocolUrl(COUNTER_PARTY_ID)).build());
            store.save(createContractNegotiationBuilder(id2).counterPartyAddress(context.providerProtocolUrl(COUNTER_PARTY_ID)).build());

            var query = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "QuerySpec")
                    .add("filterExpression", createArrayBuilder()
                            .add(createObjectBuilder()
                                    .add(TYPE, "Criterion")
                                    .add("operandLeft", "id")
                                    .add("operator", "in")
                                    .add("operandRight", createArrayBuilder().add(id1).add(id2))
                            )
                            .add(createObjectBuilder()
                                    .add(TYPE, "Criterion")
                                    .add("operandLeft", "pending")
                                    .add("operator", "=")
                                    .add("operandRight", false)
                            )
                    )
                    .build();

            var token = context.createAdminToken(oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .body(query.toString())
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/request")
                    .then()
                    .log().ifValidationFails()
                    .statusCode(200)
                    .contentType(JSON)
                    .body("size()", is(2));

        }

        @Test
        void query_shouldLimitToResourceOwner(ManagementEndToEndTestContext context, ContractNegotiationStore store) {
            var otherParticipantId = UUID.randomUUID().toString();

            var id1 = UUID.randomUUID().toString();
            var id2 = UUID.randomUUID().toString();
            store.save(createContractNegotiationBuilder(id1).counterPartyAddress(context.providerProtocolUrl(COUNTER_PARTY_ID)).build());
            store.save(createContractNegotiationBuilder(id2).participantContextId(otherParticipantId).counterPartyAddress(context.providerProtocolUrl(COUNTER_PARTY_ID)).build());

            var query = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "QuerySpec")
                    .add("filterExpression", createArrayBuilder()
                            .add(createObjectBuilder()
                                    .add(TYPE, "Criterion")
                                    .add("operandLeft", "id")
                                    .add("operator", "in")
                                    .add("operandRight", createArrayBuilder().add(id1).add(id2))
                            )
                            .add(createObjectBuilder()
                                    .add(TYPE, "Criterion")
                                    .add("operandLeft", "pending")
                                    .add("operator", "=")
                                    .add("operandRight", false)
                            )
                    )
                    .build();


            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .body(query.toString())
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/request")
                    .then()
                    .log().ifValidationFails()
                    .statusCode(200)
                    .contentType(JSON)
                    .body("size()", is(1))
                    .body("[0].@id", is(id1));

        }

        @Test
        void query_tokenBearerNotEqualResourceOwner(ManagementEndToEndTestContext context, ContractNegotiationStore store, ParticipantContextService srv) {
            var otherParticipantId = UUID.randomUUID().toString();
            srv.createParticipantContext(participantContext(otherParticipantId))
                    .orElseThrow(f -> new AssertionError("ParticipantContext " + otherParticipantId + " not created."));
            var id = UUID.randomUUID().toString();
            store.save(createContractNegotiationBuilder(id).counterPartyAddress(context.providerProtocolUrl(COUNTER_PARTY_ID)).build());

            var query = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "QuerySpec")
                    .add("filterExpression", createArrayBuilder()
                            .add(createObjectBuilder()
                                    .add(TYPE, "Criterion")
                                    .add("operandLeft", "id")
                                    .add("operator", "in")
                                    .add("operandRight", createArrayBuilder().add(id))
                            )
                            .add(createObjectBuilder()
                                    .add(TYPE, "Criterion")
                                    .add("operandLeft", "pending")
                                    .add("operator", "=")
                                    .add("operandRight", false)
                            )
                    )
                    .build();

            var token = context.createToken(otherParticipantId, oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .body(query.toString())
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/request")
                    .then()
                    .log().ifValidationFails()
                    .statusCode(403)
                    .body(containsString("User '%s' is not authorized to access this resource.".formatted(otherParticipantId)));

        }

        @Test
        void terminate(ManagementEndToEndTestContext context, ContractNegotiationStore store) {
            store.save(createContractNegotiationBuilder("cn1").build());
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(ID, "cn1")
                    .add(TYPE, "TerminateNegotiation")
                    .add("reason", "any good reason")
                    .build();

            context.baseRequest(participantTokenJwt)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1/terminate")
                    .then()
                    .log().ifError()
                    .statusCode(204);
        }

        @Test
        void terminate_tokenBearerDoesNotOwnResource(ManagementEndToEndTestContext context,
                                                     ContractNegotiationStore store,
                                                     ParticipantContextService srv) {
            store.save(createContractNegotiationBuilder("cn1").build());
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(ID, "cn1")
                    .add(TYPE, "TerminateNegotiation")
                    .add("reason", "any good reason")
                    .build();

            var otherParticipantId = UUID.randomUUID().toString();
            createParticipant(srv, otherParticipantId);

            var token = context.createToken(otherParticipantId, oauthServerSigningKey);

            context.baseRequest(token)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1/terminate")
                    .then()
                    .log().ifError()
                    .statusCode(403);
        }

        @Test
        void terminate_tokenLacksRequiredScope(ManagementEndToEndTestContext context,
                                               ContractNegotiationStore store) {
            store.save(createContractNegotiationBuilder("cn1").build());
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(ID, "cn1")
                    .add(TYPE, "TerminateNegotiation")
                    .add("reason", "any good reason")
                    .build();


            var token = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:missing"));

            context.baseRequest(token)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1/terminate")
                    .then()
                    .log().ifError()
                    .statusCode(403);
        }

        @Test
        void delete(ManagementEndToEndTestContext context, ContractNegotiationStore store) {
            store.save(createContractNegotiationBuilder("cn1")
                    .state(TERMINATED.code()).build());

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .delete("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1")
                    .then()
                    .statusCode(204);

            assertThat(store.findById("cn1")).isNull();
        }

        @Test
        void delete_shouldFailDueToWrongState(ManagementEndToEndTestContext context, ContractNegotiationStore store) {
            store.save(createContractNegotiationBuilder("cn1")
                    .state(AGREED.code()).build());

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .delete("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1")
                    .then()
                    .statusCode(409);

            assertThat(store.findById("cn1")).isNotNull();
        }

        @Test
        void delete_tokenBearerDoesNotOwnResource(ManagementEndToEndTestContext context,
                                                  ContractNegotiationStore store,
                                                  ParticipantContextService srv) {
            store.save(createContractNegotiationBuilder("cn1").build());

            var otherParticipantId = UUID.randomUUID().toString();
            createParticipant(srv, otherParticipantId);

            var token = context.createToken(otherParticipantId, oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .delete("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1")
                    .then()
                    .statusCode(403);
        }

        @Test
        void delete_tokenLacksRequiredScope(ManagementEndToEndTestContext context,
                                            ContractNegotiationStore store) {
            store.save(createContractNegotiationBuilder("cn1").build());

            var token = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:missing"));

            context.baseRequest(token)
                    .contentType(JSON)
                    .delete("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1")
                    .then()
                    .statusCode(403);
        }

        private JsonObject contractRequestJson() {
            return createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(TYPE, "ContractRequest")
                    .add("counterPartyAddress", "test-address")
                    .add("protocol", "test-protocol")
                    .add("providerId", "test-provider-id")
                    .add("callbackAddresses", createCallbackAddress())
                    .add("policy", createPolicy())
                    .build();
        }

        private ContractNegotiation.Builder createContractNegotiationBuilder(String negotiationId) {
            return ContractNegotiation.Builder.newInstance()
                    .id(negotiationId)
                    .correlationId(negotiationId)
                    .counterPartyId(randomUUID().toString())
                    .counterPartyAddress("http://counter-party/address")
                    .callbackAddresses(List.of(CallbackAddress.Builder.newInstance()
                            .uri("local://test")
                            .events(Set.of("test-event1", "test-event2"))
                            .build()))
                    .protocol("dataspace-protocol-http")
                    .state(REQUESTED.code())
                    .participantContextId(PARTICIPANT_CONTEXT_ID)
                    .contractOffer(contractOfferBuilder().build());
        }

        private ContractOffer.Builder contractOfferBuilder() {
            return ContractOffer.Builder.newInstance()
                    .id("test-offer-id")
                    .assetId(randomUUID().toString())
                    .policy(Policy.Builder.newInstance().build());
        }

        private ContractAgreement createContractAgreement(String negotiationId) {
            return ContractAgreement.Builder.newInstance()
                    .id(negotiationId)
                    .assetId(randomUUID().toString())
                    .consumerId(randomUUID() + "-consumer")
                    .providerId(randomUUID() + "-provider")
                    .policy(Policy.Builder.newInstance().type(PolicyType.CONTRACT).build())
                    .participantContextId(PARTICIPANT_CONTEXT_ID)
                    .build();
        }

        private JsonArrayBuilder createCallbackAddress() {
            var builder = Json.createArrayBuilder();
            return builder.add(createObjectBuilder()
                    .add(TYPE, "CallbackAddress")
                    .add("transactional", false)
                    .add("uri", "http://test.local/")
                    .add("events", Json.createArrayBuilder().build()));
        }

        private JsonObject createPolicy() {
            var permissionJson = createObjectBuilder().add(TYPE, "permission")
                    .add("action", "use")
                    .build();
            var prohibitionJson = createObjectBuilder().add(TYPE, "prohibition")
                    .add("action", "use")
                    .build();
            var dutyJson = createObjectBuilder().add(TYPE, "duty")
                    .add("action", "use")
                    .build();

            return createObjectBuilder()
                    .add(CONTEXT, "http://www.w3.org/ns/odrl.jsonld")
                    .add(TYPE, "Offer")
                    .add(ID, "offer-id")
                    .add("permission", createArrayBuilder().add(permissionJson))
                    .add("prohibition", createArrayBuilder().add(prohibitionJson))
                    .add("obligation", createArrayBuilder().add(dutyJson))
                    .add("assigner", "provider-id")
                    .add("target", "asset-id")
                    .build();
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

    }

}
