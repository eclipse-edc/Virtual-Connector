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
import org.assertj.core.api.Assertions;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.annotations.PostgresqlIntegrationTest;
import org.eclipse.edc.junit.extensions.ComponentRuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndExtension;
import org.eclipse.edc.test.e2e.managementapi.ManagementEndToEndTestContext;
import org.eclipse.edc.test.e2e.managementapi.Runtimes;
import org.eclipse.edc.virtualized.nats.testfixtures.NatsEndToEndExtension;
import org.eclipse.edc.virtualized.policy.cel.model.CelExpression;
import org.eclipse.edc.virtualized.policy.cel.service.CelPolicyExpressionService;
import org.eclipse.edc.virtualized.policy.cel.store.CelExpressionStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.http.ContentType.JSON;
import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2;
import static org.eclipse.edc.spi.query.Criterion.criterion;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.createParticipant;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.jsonLdContext;
import static org.eclipse.edc.virtualized.test.system.fixtures.DockerImages.createPgContainer;
import static org.eclipse.virtualized.api.management.VirtualManagementApi.EDC_V_CONNECTOR_MANAGEMENT_CONTEXT_V2;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;


public class CelExpressionApiV4EndToEndTest {


    abstract static class Tests {

        private static final String PARTICIPANT_CONTEXT_ID = "test-participant";
        @Order(0)
        @RegisterExtension
        static WireMockExtension mockJwksServer = WireMockExtension.newInstance()
                .options(wireMockConfig().dynamicPort())
                .build();
        private String adminToken;
        private ECKey oauthServerSigningKey;

        private CelExpression expression(String leftOperand, String expr) {
            return CelExpression.Builder.newInstance().id(UUID.randomUUID().toString())
                    .leftOperand(leftOperand)
                    .expression(expr)
                    .description("description")
                    .build();
        }

        @BeforeEach
        void setup(ManagementEndToEndTestContext context, ParticipantContextService participantContextService) throws JOSEException {
            createParticipant(participantContextService, PARTICIPANT_CONTEXT_ID);

            // stub JWKS endpoint at /jwks/ returning 200 OK with a simple JWKS
            oauthServerSigningKey = new ECKeyGenerator(Curve.P_256).keyID(UUID.randomUUID().toString()).generate();
            adminToken = context.createAdminToken(oauthServerSigningKey);

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
        void create(ManagementEndToEndTestContext context, CelPolicyExpressionService service) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "CelExpression")
                    .add("leftOperand", "leftOperand")
                    .add("expression", "ctx.agent.id == 'agent-123'")
                    .add("description", "desc")
                    .build();

            var id = context.baseRequest(adminToken)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .post("/v4alpha/celexpressions")
                    .then()
                    .log().ifValidationFails()
                    .contentType(JSON)
                    .statusCode(200)
                    .extract().jsonPath().getString(ID);

            assertThat(service.findById(id)).isSucceeded()
                    .satisfies(c -> {
                        Assertions.assertThat(c.getId()).isEqualTo(id);
                    });

        }

        @Test
        void create_validationFails(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "CelExpression")
                    .build();

            context.baseRequest(adminToken)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .post("/v4alpha/celexpressions")
                    .then()
                    .log().ifValidationFails()
                    .contentType(JSON)
                    .statusCode(400);
        }

        @Test
        void create_NotAdmin(ManagementEndToEndTestContext context, ParticipantContextService service) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "CelExpression")
                    .add("leftOperand", "leftOperand")
                    .add("expression", "ctx.agent.id == 'agent-123'")
                    .build();

            var token = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:write"));

            context.baseRequest(token)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .post("/v4alpha/celexpressions")
                    .then()
                    .log().ifError()
                    .statusCode(403)
                    .body(containsString("Required user role not satisfied."));
        }

        @Test
        void get(ManagementEndToEndTestContext context, CelExpressionStore store, ParticipantContextService srv) {
            var expr = expression("leftOperand", "ctx.agent.id == 'agent-123'");
            store.create(expr)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));

            context.baseRequest(adminToken)
                    .contentType(JSON)
                    .get("/v4alpha/celexpressions/" + expr.getId())
                    .then()
                    .log().ifError()
                    .statusCode(200)
                    .body(ID, is(expr.getId()))
                    .body(CONTEXT, contains(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2, EDC_V_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .body("leftOperand", is(expr.getLeftOperand()))
                    .body("expression", is(expr.getExpression()))
                    .body("description", is(expr.getDescription()))
                    .body("scopes", is(new ArrayList<>(expr.getScopes())));
        }


        @Test
        void get_NotAdmin(ManagementEndToEndTestContext context, CelExpressionStore store) {

            var expr = expression("leftOperand", "ctx.agent.id == 'agent-123'");
            store.create(expr)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));

            var token = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:read"));

            context.baseRequest(token)
                    .contentType(JSON)
                    .get("/v4alpha/celexpressions/" + expr.getId())
                    .then()
                    .log().ifError()
                    .statusCode(403)
                    .body(containsString("Required user role not satisfied."));

        }

        @Test
        void query(ManagementEndToEndTestContext context, CelExpressionStore store) {
            var expr = expression("leftOperand", "ctx.agent.id == 'agent-123'");
            store.create(expr)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));

            var matchingQuery = context.query(
                    criterion("id", "=", expr.getId())
            );

            context.baseRequest(adminToken)
                    .body(matchingQuery.toString())
                    .contentType(JSON)
                    .post("/v4alpha/celexpressions/request")
                    .then()
                    .log().ifError()
                    .statusCode(200)
                    .body("size()", is(1));


            var nonMatchingQuery = context.query(
                    criterion("id", "=", "notFound")
            );

            context.baseRequest(adminToken)
                    .body(nonMatchingQuery.toString())
                    .contentType(JSON)
                    .post("/v4alpha/celexpressions/request")
                    .then()
                    .log().ifError()
                    .statusCode(200)
                    .body("size()", is(0));
        }

        @Test
        void query_NotAdmin(ManagementEndToEndTestContext context, CelExpressionStore store) {
            var expr = expression("leftOperand", "ctx.agent.id == 'agent-123'");
            store.create(expr)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));

            var matchingQuery = context.query(
                    criterion("id", "=", expr.getId())
            );

            var token = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:read"));

            context.baseRequest(token)
                    .body(matchingQuery.toString())
                    .contentType(JSON)
                    .post("/v4alpha/celexpressions/request")
                    .then()
                    .log().ifError()
                    .statusCode(403)
                    .body(containsString("Required user role not satisfied."));

        }

        @Test
        void update(ManagementEndToEndTestContext context, CelPolicyExpressionService service) {
            var expr = expression("leftOperand", "ctx.agent.id == 'agent-123'");
            service.create(expr)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));

            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "CelExpression")
                    .add(ID, expr.getId())
                    .add("leftOperand", "leftOperand")
                    .add("expression", "ctx.agent.id == 'agent-125'")
                    .add("description", "desc")
                    .build();

            context.baseRequest(adminToken)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .put("/v4alpha/celexpressions/" + expr.getId())
                    .then()
                    .statusCode(204);

            assertThat(service.findById(expr.getId())).isSucceeded()
                    .extracting(CelExpression::getExpression)
                    .isEqualTo("ctx.agent.id == 'agent-125'");
        }


        @Test
        void update_whenSchemaValidationFails(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "CelExpression")
                    .build();

            context.baseRequest(adminToken)
                    .body(requestBody.toString())
                    .contentType(JSON)
                    .put("/v4alpha/celexpressions/id")
                    .then()
                    .log().ifValidationFails()
                    .contentType(JSON)
                    .statusCode(400);
        }

        @Test
        void update_NotAdmin(ManagementEndToEndTestContext context) {

            var requestBody = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "CelExpression")
                    .build();

            var token = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:write"));

            context.baseRequest(token)
                    .contentType(JSON)
                    .body(requestBody.toString())
                    .put("/v4alpha/celexpressions/id")
                    .then()
                    .log().ifValidationFails()
                    .statusCode(403)
                    .body(containsString("Required user role not satisfied."));

        }


        @Test
        void delete(ManagementEndToEndTestContext context, CelPolicyExpressionService service) {
            var expr = expression("leftOperand", "ctx.agent.id == 'agent-123'");
            service.create(expr)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));


            context.baseRequest(adminToken)
                    .delete("/v4alpha/celexpressions/" + expr.getId())
                    .then()
                    .statusCode(204);

            context.baseRequest(adminToken)
                    .delete("/v4alpha/celexpressions/" + expr.getId())
                    .then()
                    .statusCode(404);
        }


        @Test
        void delete_NotAdmin(ManagementEndToEndTestContext context) {

            var token = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:write"));

            context.baseRequest(token)
                    .delete("/v4alpha/celexpressions/id")
                    .then()
                    .statusCode(403)
                    .body(containsString("Required user role not satisfied."));
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
                }
            });
        }

    }

}
