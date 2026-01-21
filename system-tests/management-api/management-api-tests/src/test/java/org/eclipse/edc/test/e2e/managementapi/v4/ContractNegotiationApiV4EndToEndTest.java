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

import com.nimbusds.jose.JOSEException;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import org.eclipse.edc.api.authentication.OauthServer;
import org.eclipse.edc.api.authentication.OauthServerEndToEndExtension;
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
import org.eclipse.edc.spi.types.domain.callback.CallbackAddress;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndExtension;
import org.eclipse.edc.test.e2e.managementapi.ManagementEndToEndTestContext;
import org.eclipse.edc.test.e2e.managementapi.Runtimes;
import org.eclipse.edc.virtual.nats.testfixtures.NatsEndToEndExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
import static org.eclipse.edc.virtual.test.system.fixtures.DockerImages.createPgContainer;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesRegex;

public class ContractNegotiationApiV4EndToEndTest {

    @SuppressWarnings("JUnitMalformedDeclaration")
    abstract static class Tests {

        private static final String COUNTER_PARTY_ID = "counter-party-id";

        private String participantTokenJwt;

        @BeforeEach
        void setup(OauthServer authServer, ParticipantContextService participantContextService) throws JOSEException {
            createParticipant(participantContextService, PARTICIPANT_CONTEXT_ID);

            participantTokenJwt = authServer.createToken(PARTICIPANT_CONTEXT_ID);
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
        void initiate_whenValidationFails(ManagementEndToEndTestContext context) {

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
        void initiate_tokenBearerWrong(ManagementEndToEndTestContext context, OauthServer authServer, ParticipantContextService service) {
            var requestJson = contractRequestJson();

            var otherParticipantId = UUID.randomUUID().toString();

            service.createParticipantContext(participantContext(otherParticipantId))
                    .orElseThrow(f -> new AssertionError("ParticipantContext " + otherParticipantId + " not created."));

            var token = authServer.createToken(otherParticipantId);

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
        void initiate_tokenLacksWriteScope(ManagementEndToEndTestContext context, OauthServer authServer, ParticipantContextService service) {
            var requestJson = contractRequestJson();

            var otherParticipantId = UUID.randomUUID().toString();

            service.createParticipantContext(participantContext(otherParticipantId))
                    .orElseThrow(f -> new AssertionError("ParticipantContext " + otherParticipantId + " not created."));

            var token = authServer.createToken(PARTICIPANT_CONTEXT_ID, Map.of("scope", "management-api:read"));

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
        void initiate_tokenBearerIsAdmin(ManagementEndToEndTestContext context, OauthServer authServer, ContractNegotiationStore store) {

            var requestJson = contractRequestJson();

            var token = authServer.createAdminToken();

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
        void getById_tokenBearerDoesNotOwnResource(ManagementEndToEndTestContext context, OauthServer authServer,
                                                   ContractNegotiationStore store, ParticipantContextService srv) {
            store.save(createContractNegotiationBuilder("cn1").contractAgreement(createContractAgreement("cn1")).build());

            var otherParticipantId = UUID.randomUUID().toString();
            createParticipant(srv, otherParticipantId);

            var token = authServer.createToken(otherParticipantId);

            context.baseRequest(token)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1")
                    .then()
                    .statusCode(403);
        }

        @Test
        void getById_tokenLacksRequiredScope(ManagementEndToEndTestContext context, OauthServer authServer, ContractNegotiationStore store) {
            store.save(createContractNegotiationBuilder("cn1").contractAgreement(createContractAgreement("cn1")).build());

            var token = authServer.createToken(PARTICIPANT_CONTEXT_ID, Map.of("scope", "management-api:missing"));

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
        void getState_tokenBearerDoesNotOwnResource(ManagementEndToEndTestContext context, OauthServer authServer,
                                                    ContractNegotiationStore store, ParticipantContextService srv) {
            var state = ContractNegotiationStates.FINALIZED.code();
            store.save(createContractNegotiationBuilder("cn1").state(state).build());

            var otherParticipantId = UUID.randomUUID().toString();
            createParticipant(srv, otherParticipantId);

            var token = authServer.createToken(otherParticipantId);

            context.baseRequest(token)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1/state")
                    .then()
                    .statusCode(403);

        }

        @Test
        void getState_tokenLacksRequiredScope(ManagementEndToEndTestContext context, OauthServer authServer, ContractNegotiationStore store) {
            var state = ContractNegotiationStates.FINALIZED.code();
            store.save(createContractNegotiationBuilder("cn1").state(state).build());

            var token = authServer.createToken(PARTICIPANT_CONTEXT_ID, Map.of("scope", "management-api:missing"));

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
        void getAgreement_tokenBearerDoesNotOwnResource(ManagementEndToEndTestContext context, OauthServer authServer,
                                                        ContractNegotiationStore store, ParticipantContextService srv) {
            var agreement = createContractAgreement("cn1");
            store.save(createContractNegotiationBuilder("cn1").contractAgreement(agreement).build());

            var otherParticipantId = UUID.randomUUID().toString();
            createParticipant(srv, otherParticipantId);

            var token = authServer.createToken(otherParticipantId);

            context.baseRequest(token)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1/agreement")
                    .then()
                    .statusCode(403);

        }

        @Test
        void getAgreement_tokenLacksRequiredScope(ManagementEndToEndTestContext context, OauthServer authServer,
                                                  ContractNegotiationStore store) {
            var agreement = createContractAgreement("cn1");
            store.save(createContractNegotiationBuilder("cn1").contractAgreement(agreement).build());

            var token = authServer.createToken(PARTICIPANT_CONTEXT_ID, Map.of("scope", "management-api:missing"));

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
        void query_tokenBearerIsAdmin_shouldReturnAll(ManagementEndToEndTestContext context, OauthServer authServer, ContractNegotiationStore store) {

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

            var token = authServer.createAdminToken();

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
        void query_tokenBearerNotEqualResourceOwner(ManagementEndToEndTestContext context, OauthServer authServer, ContractNegotiationStore store, ParticipantContextService srv) {
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

            var token = authServer.createToken(otherParticipantId);

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
        void terminate_tokenBearerDoesNotOwnResource(ManagementEndToEndTestContext context, OauthServer authServer,
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

            var token = authServer.createToken(otherParticipantId);

            context.baseRequest(token)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1/terminate")
                    .then()
                    .log().ifError()
                    .statusCode(403);
        }

        @Test
        void terminate_tokenLacksRequiredScope(ManagementEndToEndTestContext context, OauthServer authServer,
                                               ContractNegotiationStore store) {
            store.save(createContractNegotiationBuilder("cn1").build());
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(ID, "cn1")
                    .add(TYPE, "TerminateNegotiation")
                    .add("reason", "any good reason")
                    .build();


            var token = authServer.createToken(PARTICIPANT_CONTEXT_ID, Map.of("scope", "management-api:missing"));

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
        void delete_tokenBearerDoesNotOwnResource(ManagementEndToEndTestContext context, OauthServer authServer,
                                                  ContractNegotiationStore store,
                                                  ParticipantContextService srv) {
            store.save(createContractNegotiationBuilder("cn1").build());

            var otherParticipantId = UUID.randomUUID().toString();
            createParticipant(srv, otherParticipantId);

            var token = authServer.createToken(otherParticipantId);

            context.baseRequest(token)
                    .contentType(JSON)
                    .delete("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/contractnegotiations/cn1")
                    .then()
                    .statusCode(403);
        }

        @Test
        void delete_tokenLacksRequiredScope(ManagementEndToEndTestContext context, OauthServer authServer,
                                            ContractNegotiationStore store) {
            store.save(createContractNegotiationBuilder("cn1").build());

            var token = authServer.createToken(PARTICIPANT_CONTEXT_ID, Map.of("scope", "management-api:missing"));

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

        @Order(0)
        @RegisterExtension
        static final OauthServerEndToEndExtension AUTH_SERVER_EXTENSION = OauthServerEndToEndExtension.Builder.newInstance().build();

        @Order(1)
        @RegisterExtension
        static RuntimeExtension runtime = ComponentRuntimeExtension.Builder.newInstance()
                .name(Runtimes.ControlPlane.NAME)
                .modules(Runtimes.ControlPlane.MODULES)
                .endpoints(Runtimes.ControlPlane.ENDPOINTS.build())
                .configurationProvider(Runtimes.ControlPlane::config)
                .paramProvider(ManagementEndToEndTestContext.class, ManagementEndToEndTestContext::forContext)
                .configurationProvider(AUTH_SERVER_EXTENSION::getConfig)
                .build();
    }

    @Nested
    @PostgresqlIntegrationTest
    class Postgres extends Tests {

        @Order(0)
        @RegisterExtension
        static final OauthServerEndToEndExtension AUTH_SERVER_EXTENSION = OauthServerEndToEndExtension.Builder.newInstance().build();

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
                .configurationProvider(NATS_EXTENSION::configFor)
                .configurationProvider(AUTH_SERVER_EXTENSION::getConfig)
                .paramProvider(ManagementEndToEndTestContext.class, ManagementEndToEndTestContext::forContext)
                .build();

    }

}
