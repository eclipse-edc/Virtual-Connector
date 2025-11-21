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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.http.ContentType.JSON;
import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.createParticipant;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.jsonLdContext;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.participantContext;
import static org.eclipse.edc.virtualized.test.system.fixtures.DockerImages.createPgContainer;
import static org.hamcrest.Matchers.equalTo;

public class ParticipantContextApiEndToEndTest {

    abstract static class Tests {

        @Order(0)
        @RegisterExtension
        static WireMockExtension mockJwksServer = WireMockExtension.newInstance()
                .options(wireMockConfig().dynamicPort())
                .build();

        static ECKey oauthServerSigningKey;

        @BeforeEach
        void setup() throws JOSEException {
            oauthServerSigningKey = new ECKeyGenerator(Curve.P_256).keyID(UUID.randomUUID().toString()).generate();

            var jwks = createObjectBuilder()
                    .add("keys", createArrayBuilder().add(createObjectBuilder(oauthServerSigningKey.toPublicJWK().toJSONObject())))
                    .build()
                    .toString();

            mockJwksServer.stubFor(any(urlPathEqualTo("/.well-known/jwks"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(jwks)));
        }

        @AfterEach
        void tearDown(ParticipantContextService pcService) {
            pcService.search(QuerySpec.max()).getContent()
                    .forEach(pc -> pcService.deleteParticipantContext(pc.getParticipantContextId()).getContent());

        }

        @Test
        void create(ManagementEndToEndTestContext context) {
            var participantContextId = "test-user";

            var body = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "ParticipantContext")
                    .add(ID, participantContextId)
                    .add("identity", participantContextId)
                    .add("properties", createObjectBuilder().add("test", "test"))
                    .build()
                    .toString();

            var token = context.createProvisionerToken(oauthServerSigningKey);

            var su = context.baseRequest(token)
                    .contentType(JSON)
                    .body(body)
                    .post("/v4alpha/participants")
                    .then()
                    .statusCode(200)
                    .extract().body().as(Map.class);
            assertThat(su.get("@id")).isEqualTo(participantContextId);
        }

        @Test
        void create_validationFails(ManagementEndToEndTestContext context) {

            var body = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "ParticipantContext")
                    .add("properties", "invalidValue") // should be an object
                    .build()
                    .toString();

            var token = context.createProvisionerToken(oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .body(body)
                    .post("/v4alpha/participants")
                    .then()
                    .statusCode(400)
                    .body("[0].message", equalTo("string found, object expected"))
                    .body("[0].path", equalTo("/properties"));
        }

        @Test
        void create_notAuthorized(ManagementEndToEndTestContext context, ParticipantContextService srv) {
            var participantContextId = "test-user";
            createParticipant(srv, participantContextId);

            var body = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "ParticipantContext")
                    .add(ID, participantContextId)
                    .add("properties", createObjectBuilder().add("test", "test"))
                    .build()
                    .toString();

            var token = context.createToken(participantContextId, oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .body(body)
                    .post("/v4alpha/participants")
                    .then()
                    .statusCode(403);

        }

        @Test
        void getParticipantContext(ManagementEndToEndTestContext context, ParticipantContextService srv) {
            var participantContextId = "test-user";
            Map<String, Object> properties = Map.of("key1", "value1", "key2", "value2");
            createParticipant(srv, participantContextId, properties);

            var token = context.createAdminToken(oauthServerSigningKey);

            var su = context.baseRequest(token)
                    .get("/v4alpha/participants/" + participantContextId)
                    .then()
                    .statusCode(200)
                    .extract().body().as(Map.class);
            assertThat(su.get("@id")).isEqualTo(participantContextId);
            assertThat(su.get("properties")).isEqualTo(properties);
        }

        @Test
        void getParticipantContext_notAuthorized(ManagementEndToEndTestContext context, ParticipantContextService srv) {
            var participantContextId = "test-user";
            createParticipant(srv, participantContextId);

            var token = context.createToken(participantContextId, oauthServerSigningKey);

            context.baseRequest(token)
                    .get("/v4alpha/participants/" + participantContextId)
                    .then()
                    .statusCode(403);

        }

        @Test
        void update(ManagementEndToEndTestContext context, ParticipantContextService service) {
            var participantContextId = "test-user";

            service.createParticipantContext(participantContext(participantContextId));

            var props = Map.of("newKey", "newValue");
            var body = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "ParticipantContext")
                    .add(ID, participantContextId)
                    .add("identity", participantContextId)
                    .add("properties", createObjectBuilder(props))
                    .build()
                    .toString();

            var token = context.createProvisionerToken(oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .body(body)
                    .put("/v4alpha/participants/" + participantContextId)
                    .then()
                    .statusCode(204);

            var ctx = service.getParticipantContext(participantContextId).orElseThrow(f -> new AssertionError("Participant not found"));

            assertThat(ctx.getProperties()).isEqualTo(props);
        }

        @Test
        void update_validationFails(ManagementEndToEndTestContext context) {

            var body = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "ParticipantContext")
                    .add("properties", "invalidValue") // should be an object
                    .build()
                    .toString();

            var token = context.createProvisionerToken(oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .body(body)
                    .put("/v4alpha/participants/1")
                    .then()
                    .statusCode(400)
                    .body("[0].message", equalTo("string found, object expected"))
                    .body("[0].path", equalTo("/properties"));
        }

        @Test
        void update_notAuthorized(ManagementEndToEndTestContext context, ParticipantContextService srv) {
            var participantContextId = "test-user";
            createParticipant(srv, participantContextId);

            var body = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "ParticipantContext")
                    .add(ID, participantContextId)
                    .add("properties", createObjectBuilder().add("test", "test"))
                    .build()
                    .toString();

            var token = context.createToken(participantContextId, oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .body(body)
                    .put("/v4alpha/participants/1")
                    .then()
                    .statusCode(403);

        }

        @Test
        void query(ManagementEndToEndTestContext context, ParticipantContextService service) {

            range(0, 10).forEach(i -> {
                var participantContextId = "user" + i;
                service.createParticipantContext(participantContext(participantContextId));
            });

            var token = context.createProvisionerToken(oauthServerSigningKey);
            context.baseRequest(token)
                    .contentType(JSON)
                    .get("/v4alpha/participants")
                    .then()
                    .statusCode(200)
                    .body("size()", equalTo(10));
        }

        @Test
        void query_withPaging(ManagementEndToEndTestContext context, ParticipantContextService service) {

            range(0, 10).forEach(i -> {
                var participantContextId = "user" + i;
                service.createParticipantContext(participantContext(participantContextId));
            });

            var token = context.createProvisionerToken(oauthServerSigningKey);
            context.baseRequest(token)
                    .contentType(JSON)
                    .get("/v4alpha/participants?offset=2&limit=4")
                    .then()
                    .statusCode(200)
                    .body("size()", equalTo(4));
        }

        @Test
        void query_notAuthorized(ManagementEndToEndTestContext context, ParticipantContextService service) {

            var otherParticipantId = "test-user";
            createParticipant(service, otherParticipantId);

            range(0, 10).forEach(i -> {
                var participantContextId = "user" + i;
                service.createParticipantContext(participantContext(participantContextId));
            });

            var token = context.createToken(otherParticipantId, oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .get("/v4alpha/participants")
                    .then()
                    .statusCode(403);
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
