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
import io.restassured.http.ContentType;
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.annotations.PostgresqlIntegrationTest;
import org.eclipse.edc.junit.extensions.ComponentRuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.query.QuerySpec;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.http.ContentType.JSON;
import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.spi.query.Criterion.criterion;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.createParticipant;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.jsonLdContext;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.jsonLdContextArray;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.participantContext;
import static org.eclipse.edc.virtualized.test.system.fixtures.DockerImages.createPgContainer;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

public class PolicyDefinitionApiV4EndToEndTest {

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
        void teardown(ParticipantContextService participantContextService, PolicyDefinitionStore store) {
            participantContextService.deleteParticipantContext(PARTICIPANT_CONTEXT_ID)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));

            store.findAll(QuerySpec.max()).forEach(pd -> store.delete(pd.getId()));
        }

        @Test
        void create(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyDefinition")
                    .add("policy", sampleOdrlPolicy())
                    .build();

            var id = context.baseRequest(participantTokenJwt)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions")
                    .then()
                    .log().ifValidationFails()
                    .contentType(JSON)
                    .statusCode(200)
                    .extract().jsonPath().getString(ID);

            context.baseRequest(participantTokenJwt)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + id)
                    .then()
                    .log().ifValidationFails()
                    .statusCode(200)
                    .contentType(JSON)
                    .body(ID, is(id))
                    .body(CONTEXT, contains(jsonLdContextArray()))
                    .body("policy.permission[0].constraint[0].leftOperand", is("inForceDate"))
                    .body("policy.permission[0].constraint[0].operator", is("gteq"))
                    .body("policy.permission[0].constraint[0].rightOperand", is("contractAgreement+0s"))
                    .body("policy.prohibition[0].action", is("use"))
                    .body("policy.obligation[0].action", is("use"));
        }

        @Test
        void create_WithPrivateProperties(ManagementEndToEndTestContext context, PolicyDefinitionStore store) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyDefinition")
                    .add("policy", sampleOdrlPolicy())
                    .add("privateProperties", createObjectBuilder()
                            .add("newKey", "newValue")
                            .build())
                    .build();

            var id = context.baseRequest(participantTokenJwt)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions")
                    .then()
                    .contentType(JSON)
                    .extract().jsonPath().getString(ID);

            var result = store.findById(id);

            assertThat(result).isNotNull()
                    .extracting(PolicyDefinition::getPolicy).isNotNull()
                    .extracting(Policy::getPermissions).asList().hasSize(1);
            Map<String, Object> privateProp = new HashMap<>();
            privateProp.put("https://w3id.org/edc/v0.0.1/ns/newKey", "newValue");
            assertThat(result).isNotNull()
                    .extracting(PolicyDefinition::getPrivateProperties).isEqualTo(privateProp);

            context.baseRequest(participantTokenJwt)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + id)
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .body(ID, is(id))
                    .body(CONTEXT, contains(jsonLdContextArray()))
                    .log().all()
                    .body("policy.permission[0].constraint[0].operator", is("gteq"));
        }

        @Test
        void create_validationFails(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyDefinition")
                    .build();

            context.baseRequest(participantTokenJwt)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions")
                    .then()
                    .log().ifValidationFails()
                    .contentType(JSON)
                    .statusCode(400);
        }

        @Test
        void create_tokenBearerWrong(ManagementEndToEndTestContext context, ParticipantContextService service) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyDefinition")
                    .add("policy", sampleOdrlPolicy())
                    .add("privateProperties", createObjectBuilder()
                            .add("newKey", "newValue")
                            .build())
                    .build();
            var id = "other-participant";

            service.createParticipantContext(participantContext(id))
                    .orElseThrow(f -> new AssertionError("ParticipantContext " + id + " not created."));

            var token = context.createToken(id, oauthServerSigningKey);

            context.baseRequest(token)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions")
                    .then()
                    .log().ifError()
                    .contentType(JSON)
                    .statusCode(403)
                    .body(containsString("User '%s' is not authorized to access this resource.".formatted(id)));
        }

        @Test
        void create_tokenLacksWriteScope(ManagementEndToEndTestContext context, ParticipantContextService service) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyDefinition")
                    .add("policy", sampleOdrlPolicy())
                    .add("privateProperties", createObjectBuilder()
                            .add("newKey", "newValue")
                            .build())
                    .build();

            var token = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:read"));

            context.baseRequest(token)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions")
                    .then()
                    .log().ifError()
                    .statusCode(403)
                    .body(matchesRegex("(?s).*Required scope.*missing.*"));
        }

        @Test
        void create_tokenBearerIsAdmin(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyDefinition")
                    .add("policy", sampleOdrlPolicy())
                    .add("privateProperties", createObjectBuilder()
                            .add("newKey", "newValue")
                            .build())
                    .build();
            var token = context.createAdminToken(oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(ContentType.JSON)
                    .body(requestBody.toString())
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions")
                    .then()
                    .log().ifValidationFails()
                    .statusCode(200);
        }

        @Test
        void create_tokenBearerIsAdmin_participantNotFound(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyDefinition")
                    .add("policy", sampleOdrlPolicy())
                    .add("privateProperties", createObjectBuilder()
                            .add("newKey", "newValue")
                            .build())
                    .build();
            var token = context.createAdminToken(oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(ContentType.JSON)
                    .body(requestBody.toString())
                    .post("/v4alpha/participants/not-valid/policydefinitions")
                    .then()
                    .log().ifValidationFails()
                    .statusCode(404);
        }

        @Test
        void get(ManagementEndToEndTestContext context, PolicyDefinitionStore store, ParticipantContextService srv) {

            var policy = PolicyDefinition.Builder.newInstance()
                    .participantContextId(PARTICIPANT_CONTEXT_ID)
                    .policy(Policy.Builder.newInstance().build())
                    .build();
            var stored = store.create(policy)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + stored.getId())
                    .then()
                    .log().ifError()
                    .statusCode(200)
                    .body(ID, is(stored.getId()))
                    .body(CONTEXT, contains(jsonLdContextArray()));
        }

        @Test
        void get_tokenBearerDoesNotOwnResource(ManagementEndToEndTestContext context, PolicyDefinitionStore store, ParticipantContextService srv) {

            var otherParticipantId = UUID.randomUUID().toString();
            createParticipant(srv, otherParticipantId);

            var policy = PolicyDefinition.Builder.newInstance()
                    .participantContextId(PARTICIPANT_CONTEXT_ID)
                    .policy(Policy.Builder.newInstance().build())
                    .build();
            var stored = store.create(policy)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));
            var token = context.createToken(otherParticipantId, oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + stored.getId())
                    .then()
                    .log().ifError()
                    .statusCode(403);
        }

        @Test
        void get_tokenLacksRequiredScope(ManagementEndToEndTestContext context, PolicyDefinitionStore store, ParticipantContextService srv) {

            var policy = PolicyDefinition.Builder.newInstance()
                    .participantContextId(PARTICIPANT_CONTEXT_ID)
                    .policy(Policy.Builder.newInstance().build())
                    .build();
            var stored = store.create(policy)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));
            var token = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:missing"));

            context.baseRequest(token)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + stored.getId())
                    .then()
                    .log().ifError()
                    .statusCode(403);

        }

        @Test
        void query_WithSimplePrivateProperties(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyDefinition")
                    .add("policy", sampleOdrlPolicy())
                    .add("privateProperties", createObjectBuilder()
                            .add("newKey", createObjectBuilder().add(ID, "newValue"))
                            .build())
                    .build();

            var id = context.baseRequest(participantTokenJwt)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions")
                    .then()
                    .contentType(JSON)
                    .extract().jsonPath().getString(ID);

            var matchingQuery = context.query(
                    criterion("id", "=", id),
                    criterion("privateProperties.'https://w3id.org/edc/v0.0.1/ns/newKey'.@id", "=", "newValue")
            );

            context.baseRequest(participantTokenJwt)
                    .body(matchingQuery.toString())
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/request")
                    .then()
                    .log().ifError()
                    .statusCode(200)
                    .body("size()", is(1));


            var nonMatchingQuery = context.query(
                    criterion("id", "=", id),
                    criterion("privateProperties.'https://w3id.org/edc/v0.0.1/ns/newKey'.@id", "=", "somethingElse")
            );

            context.baseRequest(participantTokenJwt)
                    .body(nonMatchingQuery.toString())
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/request")
                    .then()
                    .log().ifError()
                    .statusCode(200)
                    .body("size()", is(0));
        }

        @Test
        void query_tokenBearerIsAdmin_shouldReturnAll(ManagementEndToEndTestContext context, PolicyDefinitionStore store) {
            IntStream.range(0, 10)
                    .forEach(i -> {
                        store.create(PolicyDefinition.Builder.newInstance().policy(Policy.Builder.newInstance().build())
                                        .participantContextId(PARTICIPANT_CONTEXT_ID).build())
                                .orElseThrow(f -> new AssertionError(f.getFailureDetail()));

                    });

            var token = context.createAdminToken(oauthServerSigningKey);

            var result = context.baseRequest(token)
                    .contentType(ContentType.JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/request")
                    .then()
                    .log().ifError()
                    .statusCode(200)
                    .extract().body().as(Map[].class);

            assertThat(result).isNotNull().hasSize(10);

        }

        @Test
        void query_shouldLimitToResourceOwner(ManagementEndToEndTestContext context, PolicyDefinitionStore store) {
            var otherParticipantId = UUID.randomUUID().toString();

            var ownPolicy = store.create(PolicyDefinition.Builder.newInstance().policy(Policy.Builder.newInstance().build())
                            .participantContextId(PARTICIPANT_CONTEXT_ID).build())
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));

            store.create(PolicyDefinition.Builder.newInstance().policy(Policy.Builder.newInstance().build())
                            .participantContextId(otherParticipantId).build())
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));

            var token = context.createAdminToken(oauthServerSigningKey);

            var result = context.baseRequest(token)
                    .contentType(ContentType.JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/request")
                    .then()
                    .log().ifError()
                    .statusCode(200)
                    .extract().body().as(Map[].class);

            assertThat(result).isNotNull().hasSize(1)
                    .allMatch(m -> m.get("@id").equals(ownPolicy.getId()));
        }

        @Test
        void query_tokenBearerNotEqualResourceOwner(ManagementEndToEndTestContext context, PolicyDefinitionStore store, ParticipantContextService srv) {
            var participantId = UUID.randomUUID().toString();
            createParticipant(srv, participantId);

            var token = context.createToken(participantId, oauthServerSigningKey);

            store.create(PolicyDefinition.Builder.newInstance().policy(Policy.Builder.newInstance().build())
                            .participantContextId(PARTICIPANT_CONTEXT_ID).build())
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));

            var query = context.query(criterion("foo", "=", "bar")).toString();

            context.baseRequest(token)
                    .contentType(ContentType.JSON)
                    .body(query)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/assets/request")
                    .then()
                    .log().ifError()
                    .statusCode(403)
                    .body(containsString("User '%s' is not authorized to access this resource.".formatted(participantId)));

        }

        @Test
        void update(ManagementEndToEndTestContext context, PolicyDefinitionStore store) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyDefinition")
                    .add("policy", sampleOdrlPolicy())
                    .build();

            var id = context.baseRequest(participantTokenJwt)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions")
                    .then()
                    .statusCode(200)
                    .extract().jsonPath().getString(ID);

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + id)
                    .then()
                    .statusCode(200);

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .body(createObjectBuilder(requestBody)
                            .add(ID, id)
                            .add("privateProperties", createObjectBuilder().add("privateProperty", "value"))
                            .build()
                            .toString())
                    .put("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + id)
                    .then()
                    .statusCode(204);

            assertThat(store.findById(id))
                    .extracting(PolicyDefinition::getPrivateProperties)
                    .asInstanceOf(MAP)
                    .isNotEmpty();
        }

        @Test
        void update_whenPolicyValidationFails(ManagementEndToEndTestContext context) {
            var validRequestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyDefinition")
                    .add("policy", emptyOdrlPolicy())
                    .build();

            var id = context.baseRequest(participantTokenJwt)
                    .body(validRequestBody.toString())
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions")
                    .then()
                    .contentType(JSON)
                    .extract().jsonPath().getString(ID);

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + id)
                    .then()
                    .statusCode(200);

            var inValidRequestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyDefinition")
                    .add("policy", sampleInvalidOdrlPolicy())
                    .build();

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .body(inValidRequestBody.toString())
                    .put("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + id)
                    .then()
                    .statusCode(400)
                    .contentType(JSON)
                    .body("size()", is(2))
                    .body("[0].message", startsWith("leftOperand 'https://w3id.org/edc/v0.0.1/ns/left' is not bound to any scopes"))
                    .body("[1].message", startsWith("leftOperand 'https://w3id.org/edc/v0.0.1/ns/left' is not bound to any functions"));
        }

        @Test
        void update_whenSchemaValidationFails(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyDefinition")
                    .build();

            context.baseRequest(participantTokenJwt)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .put("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/id")
                    .then()
                    .log().ifValidationFails()
                    .contentType(JSON)
                    .statusCode(400);
        }

        @Test
        void update_tokenBearerDoesNotOwnResource(ManagementEndToEndTestContext context, PolicyDefinitionStore store, ParticipantContextService srv) {

            var policy = PolicyDefinition.Builder.newInstance()
                    .participantContextId(PARTICIPANT_CONTEXT_ID)
                    .policy(Policy.Builder.newInstance().build())
                    .build();
            var stored = store.create(policy)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));

            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyDefinition")
                    .add("policy", sampleOdrlPolicy())
                    .build();
            var otherParticipantId = UUID.randomUUID().toString();
            createParticipant(srv, otherParticipantId);
            var token = context.createToken(otherParticipantId, oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .body(createObjectBuilder(requestBody)
                            .add(ID, stored.getId())
                            .add("privateProperties", createObjectBuilder().add("privateProperty", "value"))
                            .build()
                            .toString())
                    .put("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + stored.getId())
                    .then()
                    .statusCode(403);

        }

        @Test
        void update_tokenLacksRequiredScope(ManagementEndToEndTestContext context, PolicyDefinitionStore store, ParticipantContextService srv) {

            var policy = PolicyDefinition.Builder.newInstance()
                    .participantContextId(PARTICIPANT_CONTEXT_ID)
                    .policy(Policy.Builder.newInstance().build())
                    .build();
            var stored = store.create(policy)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));

            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyDefinition")
                    .add("policy", sampleOdrlPolicy())
                    .build();

            var token = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:read"));

            context.baseRequest(token)
                    .contentType(JSON)
                    .body(createObjectBuilder(requestBody)
                            .add(ID, stored.getId())
                            .add("privateProperties", createObjectBuilder().add("privateProperty", "value"))
                            .build()
                            .toString())
                    .put("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + stored.getId())
                    .then()
                    .statusCode(403);

        }


        @Test
        void delete(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyDefinition")
                    .add("policy", sampleOdrlPolicy())
                    .build();

            var id = context.baseRequest(participantTokenJwt)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions")
                    .then()
                    .log().ifError()
                    .statusCode(200)
                    .extract().jsonPath().getString(ID);

            context.baseRequest(participantTokenJwt)
                    .delete("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + id)
                    .then()
                    .statusCode(204);

            context.baseRequest(participantTokenJwt)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + id)
                    .then()
                    .statusCode(404);
        }

        @Test
        void delete_withProperties(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyDefinition")
                    .add("policy", sampleOdrlPolicy())
                    .add("privateProperties", createObjectBuilder()
                            .add("newKey", "newValue")
                            .build())
                    .build();

            var id = context.baseRequest(participantTokenJwt)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions")
                    .then()
                    .statusCode(200)
                    .extract().jsonPath().getString(ID);

            context.baseRequest(participantTokenJwt)
                    .delete("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + id)
                    .then()
                    .statusCode(204);

            context.baseRequest(participantTokenJwt)
                    .get("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + id)
                    .then()
                    .statusCode(404);
        }

        @Test
        void delete_tokenBearerDoesNotOwnResource(ManagementEndToEndTestContext context, PolicyDefinitionStore store, ParticipantContextService srv) {

            var policy = PolicyDefinition.Builder.newInstance()
                    .participantContextId(PARTICIPANT_CONTEXT_ID)
                    .policy(Policy.Builder.newInstance().build())
                    .build();
            var stored = store.create(policy)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));

            var otherParticipantId = UUID.randomUUID().toString();
            createParticipant(srv, otherParticipantId);
            var token = context.createToken(otherParticipantId, oauthServerSigningKey);

            context.baseRequest(token)
                    .delete("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + stored.getId())
                    .then()
                    .statusCode(403);
        }

        @Test
        void delete_tokenLacksRequiredScope(ManagementEndToEndTestContext context, PolicyDefinitionStore store, ParticipantContextService srv) {

            var policy = PolicyDefinition.Builder.newInstance()
                    .participantContextId(PARTICIPANT_CONTEXT_ID)
                    .policy(Policy.Builder.newInstance().build())
                    .build();
            var stored = store.create(policy)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));


            var token = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:read"));

            context.baseRequest(token)
                    .delete("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + stored.getId())
                    .then()
                    .statusCode(403);

        }

        @Test
        void validate(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyDefinition")
                    .add("policy", sampleInvalidOdrlPolicy())
                    .build();

            context.baseRequest(participantTokenJwt)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions")
                    .then()
                    .log().ifError()
                    .statusCode(400)
                    .body("size()", is(2))
                    .body("[0].message", startsWith("leftOperand 'https://w3id.org/edc/v0.0.1/ns/left' is not bound to any scopes"))
                    .body("[1].message", startsWith("leftOperand 'https://w3id.org/edc/v0.0.1/ns/left' is not bound to any functions"));
        }

        @Test
        void evaluationPlan(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyDefinition")
                    .add("policy", sampleOdrlPolicy())
                    .build();

            var id = context.baseRequest(participantTokenJwt)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions")
                    .then()
                    .statusCode(200)
                    .extract().jsonPath().getString(ID);

            var planBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyEvaluationPlanRequest")
                    .add("policyScope", "catalog")
                    .build();

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .body(planBody.toString())
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + id + "/evaluationplan")
                    .then()
                    .statusCode(200)
                    .body("preValidators.size()", is(0))
                    .body("permissionSteps[0].isFiltered", is(false))
                    .body("permissionSteps[0].filteringReasons.size()", is(0))
                    .body("permissionSteps[0].constraintSteps[0].'@type'", is("AtomicConstraintStep"))
                    .body("permissionSteps[0].constraintSteps[0].isFiltered", is(true))
                    .body("permissionSteps[0].constraintSteps[0].filteringReasons.size()", is(2))
                    .body("permissionSteps[0].constraintSteps[0].functionName", nullValue())
                    .body("permissionSteps[0].constraintSteps[0].functionParams.size()", is(3))
                    .body("prohibitionSteps[0].isFiltered", is(false))
                    .body("prohibitionSteps[0].filteringReasons", notNullValue())
                    .body("prohibitionSteps[0].constraintSteps.size()", is(0))
                    .body("obligationSteps[0].isFiltered", is(false))
                    .body("obligationSteps[0].filteringReasons.size()", is(0))
                    .body("obligationSteps[0].constraintSteps.size()", is(0))
                    .body("postValidators.size()", is(0));

        }

        @Test
        void evaluationPlan_tokenBearerDoesNotOwnResource(ManagementEndToEndTestContext context, PolicyDefinitionStore store, ParticipantContextService srv) {

            var planBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyEvaluationPlanRequest")
                    .add("policyScope", "catalog")
                    .build();

            var policy = PolicyDefinition.Builder.newInstance()
                    .participantContextId(PARTICIPANT_CONTEXT_ID)
                    .policy(Policy.Builder.newInstance().build())
                    .build();
            var stored = store.create(policy)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));

            var otherParticipantId = UUID.randomUUID().toString();
            createParticipant(srv, otherParticipantId);
            var token = context.createToken(otherParticipantId, oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .body(planBody.toString())
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + stored.getId() + "/evaluationplan")
                    .then()
                    .log().ifError()
                    .statusCode(403);
        }

        @Test
        void evaluationPlan_tokenLacksRequiredScope(ManagementEndToEndTestContext context, PolicyDefinitionStore store, ParticipantContextService srv) {
            var planBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "PolicyEvaluationPlanRequest")
                    .add("policyScope", "catalog")
                    .build();

            var policy = PolicyDefinition.Builder.newInstance()
                    .participantContextId(PARTICIPANT_CONTEXT_ID)
                    .policy(Policy.Builder.newInstance().build())
                    .build();
            var stored = store.create(policy)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));


            var token = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:missing"));

            context.baseRequest(token)
                    .contentType(JSON)
                    .body(planBody.toString())
                    .post("/v4alpha/participants/" + PARTICIPANT_CONTEXT_ID + "/policydefinitions/" + stored.getId() + "/evaluationplan")
                    .then()
                    .statusCode(403);

        }

        private JsonObject emptyOdrlPolicy() {
            return createObjectBuilder()
                    .add(TYPE, "Set")
                    .build();
        }

        private JsonObject sampleInvalidOdrlPolicy() {
            return createObjectBuilder()
                    .add(TYPE, "Set")
                    .add("permission", createArrayBuilder()
                            .add(createObjectBuilder()
                                    .add("action", "use")
                                    .add("constraint", createArrayBuilder().add(createObjectBuilder()
                                                    .add("leftOperand", "https://w3id.org/edc/v0.0.1/ns/left")
                                                    .add("operator", "eq")
                                                    .add("rightOperand", "value"))
                                            .build()))
                            .build())
                    .build();
        }

        private JsonObject sampleOdrlPolicy() {
            return createObjectBuilder()
                    .add(TYPE, "Set")
                    .add("permission", createArrayBuilder()
                            .add(createObjectBuilder()
                                    .add("action", "use")
                                    .add("constraint", createArrayBuilder().add(createObjectBuilder()
                                                    .add("leftOperand", "inForceDate")
                                                    .add("operator", "gteq")
                                                    .add("rightOperand", "contractAgreement+0s"))
                                            .build()))
                            .build())
                    .add("prohibition", createArrayBuilder()
                            .add(createObjectBuilder()
                                    .add("action", "use")
                            ))
                    .add("obligation", createArrayBuilder()
                            .add(createObjectBuilder()
                                    .add("action", "use")
                            )
                    )
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

    }

}
