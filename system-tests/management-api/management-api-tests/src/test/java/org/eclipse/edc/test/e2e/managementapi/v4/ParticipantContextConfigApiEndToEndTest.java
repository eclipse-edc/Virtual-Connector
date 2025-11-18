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
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.annotations.PostgresqlIntegrationTest;
import org.eclipse.edc.junit.extensions.ComponentRuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.participantcontext.spi.config.model.ParticipantContextConfiguration;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
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
import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.createParticipant;
import static org.eclipse.edc.test.e2e.managementapi.v4.TestFunction.jsonLdContext;
import static org.eclipse.edc.virtualized.test.system.fixtures.DockerImages.createPgContainer;
import static org.hamcrest.Matchers.equalTo;

public class ParticipantContextConfigApiEndToEndTest {

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
        void set(ManagementEndToEndTestContext context, ParticipantContextConfigService service) {
            var participantContextId = "test-user";

            var entries = Map.of("key1", "value1", "key2", "value2");

            var body = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "ParticipantContextConfig")
                    .add("entries", createObjectBuilder(entries))
                    .build()
                    .toString();

            var token = context.createProvisionerToken(oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(ContentType.JSON)
                    .body(body)
                    .put("/v4alpha/participants/" + participantContextId + "/config")
                    .then()
                    .log().all()
                    .statusCode(204);

            var cfg = service.get(participantContextId)
                    .orElseThrow(f -> new AssertionError("Participant context config not found"));

            assertThat(cfg.getEntries()).isEqualTo(entries);
        }

        @Test
        void set_validationFails(ManagementEndToEndTestContext context) {
            var participantContextId = "test-user";

            var body = createObjectBuilder()
                    .add(CONTEXT, jsonLdContext())
                    .add(TYPE, "ParticipantContextConfig")
                    .add("entries", "invalidValue") // should be an object
                    .build()
                    .toString();

            var token = context.createProvisionerToken(oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(ContentType.JSON)
                    .body(body)
                    .put("/v4alpha/participants/" + participantContextId + "/config")
                    .then()
                    .statusCode(400)
                    .body("[0].message", equalTo("string found, object expected"))
                    .body("[0].path", equalTo("/entries"));
        }

        @Test
        void set_notAuthorized(ManagementEndToEndTestContext context, ParticipantContextService srv) {
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
                    .contentType(ContentType.JSON)
                    .body(body)
                    .put("/v4alpha/participants/" + participantContextId + "/config")
                    .then()
                    .statusCode(403);

        }

        @Test
        void get(ManagementEndToEndTestContext context, ParticipantContextConfigService srv) {
            var participantContextId = "test-user";
            var entries = Map.of("key1", "value1", "key2", "value2");

            srv.save(ParticipantContextConfiguration.Builder.newInstance().participantContextId(participantContextId)
                    .entries(entries)
                    .build());
            var token = context.createAdminToken(oauthServerSigningKey);

            var su = context.baseRequest(token)
                    .get("/v4alpha/participants/" + participantContextId + "/config")
                    .then()
                    .statusCode(200)
                    .extract().body().as(Map.class);
            assertThat(su.get("entries")).isEqualTo(entries);
        }

        @Test
        void get_notAuthorized(ManagementEndToEndTestContext context, ParticipantContextService srv) {
            var participantContextId = "test-user";
            createParticipant(srv, participantContextId);

            var token = context.createToken(participantContextId, oauthServerSigningKey);

            context.baseRequest(token)
                    .get("/v4alpha/participants/" + participantContextId + "/config")
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
