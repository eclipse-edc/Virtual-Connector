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

package org.eclipse.edc.virtual;

import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.annotations.PostgresqlIntegrationTest;
import org.eclipse.edc.junit.extensions.ComponentRuntimeContext;
import org.eclipse.edc.junit.extensions.ComponentRuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.utils.Endpoints;
import org.eclipse.edc.junit.utils.LazySupplier;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndExtension;
import org.eclipse.edc.virtual.nats.testfixtures.NatsEndToEndExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.util.HashMap;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.eclipse.edc.virtual.test.system.fixtures.DockerImages.createPgContainer;
import static org.hamcrest.Matchers.equalTo;

@SuppressWarnings("JUnitMalformedDeclaration")
public class RuntimeSmokeTests {

    static final Endpoints.Builder ENDPOINTS = Endpoints.Builder.newInstance()
            .endpoint("default", () -> URI.create("http://localhost:" + getFreePort() + "/api"))
            .endpoint("protocol", () -> URI.create("http://localhost:" + getFreePort() + "/control"));

    static DefaultEndpoint defaultEndpoint(ComponentRuntimeContext ctx) {
        return new DefaultEndpoint(ctx.getEndpoint("default"));
    }

    private static Config config() {
        return ConfigFactory.fromMap(new HashMap<>() {
            {
                put("edc.iam.sts.oauth.token.url", "https://sts.com/token");
                put("edc.iam.sts.oauth.client.id", "test-client");
                put("edc.iam.sts.oauth.client.secret.alias", "test-alias");
                put("edc.iam.issuer.id", "did:web:someone");
                put("edc.iam.oauth2.issuer", "test-issuer");
                put("edc.iam.oauth2.jwks.cache.validity", "0");
                put("edc.iam.oauth2.jwks.url", "https://example.com/jwks");
            }
        });
    }

    abstract static class SmokeTest {

        @Test
        void assertRuntimeReady(DefaultEndpoint endpoint) {
            await().untilAsserted(() -> given()
                    .baseUri(endpoint.get() + "/check/startup")
                    .get()
                    .then()
                    .statusCode(200)
                    .log().ifValidationFails()
                    .body("isSystemHealthy", equalTo(true)));

        }
    }

    private static class DefaultEndpoint {
        private final LazySupplier<URI> supplier;

        private DefaultEndpoint(LazySupplier<URI> supplier) {
            this.supplier = supplier;
        }

        URI get() {
            return supplier.get();
        }
    }

    @Nested
    @EndToEndTest
    class ControlPlaneMemoryDcp extends SmokeTest {

        @RegisterExtension
        static final RuntimeExtension RUNTIME = ComponentRuntimeExtension.Builder.newInstance()
                .name("control-plane-memory")
                .modules(":dist:bom:virtual-controlplane-memory-bom", ":dist:bom:virtual-controlplane-feature-dcp-bom")

                .endpoints(ENDPOINTS.build())
                .configurationProvider(RuntimeSmokeTests::config)
                .paramProvider(DefaultEndpoint.class, RuntimeSmokeTests::defaultEndpoint)
                .build();


    }

    @Nested
    @PostgresqlIntegrationTest
    @Testcontainers
    class ControlPlanePgDcp extends SmokeTest {

        @Order(0)
        @RegisterExtension
        static final NatsEndToEndExtension NATS_EXTENSION = new NatsEndToEndExtension();

        @Order(0)
        @RegisterExtension
        static final PostgresqlEndToEndExtension POSTGRESQL_EXTENSION = new PostgresqlEndToEndExtension(createPgContainer());
        static final String DB_NAME = "smoke_test";


        @Order(1)
        @RegisterExtension
        static final BeforeAllCallback CREATE_DATABASES = context -> {
            POSTGRESQL_EXTENSION.createDatabase(DB_NAME);
        };
        @RegisterExtension
        @Order(2)
        static final RuntimeExtension RUNTIME = ComponentRuntimeExtension.Builder.newInstance()
                .name("control-plane-pg")
                .modules(":dist:bom:virtual-controlplane-memory-bom",
                        ":dist:bom:virtual-controlplane-feature-dcp-bom",
                        ":dist:bom:virtual-controlplane-feature-sql-bom",
                        ":dist:bom:virtual-controlplane-feature-nats-bom",
                        ":dist:bom:virtual-controlplane-feature-nats-cdc")
                .endpoints(ENDPOINTS.build())
                .configurationProvider(RuntimeSmokeTests::config)
                .configurationProvider(ControlPlanePgDcp::config)
                .configurationProvider(NATS_EXTENSION::configFor)
                .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(DB_NAME))
                .paramProvider(DefaultEndpoint.class, RuntimeSmokeTests::defaultEndpoint)
                .build();

        private static Config config() {
            return ConfigFactory.fromMap(new HashMap<>() {
                {
                    put("edc.iam.oauth2.issuer", "test-issuer");
                    put("edc.iam.oauth2.jwks.cache.validity", "0");
                    put("edc.iam.oauth2.jwks.url", "https://example.com/jwks");
                    put("edc.postgres.cdc.url", POSTGRESQL_EXTENSION.getJdbcUrl(DB_NAME));
                    put("edc.postgres.cdc.user", POSTGRESQL_EXTENSION.getUsername());
                    put("edc.postgres.cdc.password", POSTGRESQL_EXTENSION.getPassword());
                    put("edc.postgres.cdc.slot", "edc_cdc_slot_" + Runtimes.ControlPlane.NAME.toLowerCase());
                }
            });
        }
    }

    @Nested
    @PostgresqlIntegrationTest
    @Testcontainers
    class CdcAgent extends SmokeTest {

        @Order(0)
        @RegisterExtension
        static final NatsEndToEndExtension NATS_EXTENSION = new NatsEndToEndExtension();

        @Order(0)
        @RegisterExtension
        static final PostgresqlEndToEndExtension POSTGRESQL_EXTENSION = new PostgresqlEndToEndExtension(createPgContainer());
        static final String DB_NAME = "smoke_test";


        @Order(1)
        @RegisterExtension
        static final BeforeAllCallback CREATE_DATABASES = context -> {
            POSTGRESQL_EXTENSION.createDatabase(DB_NAME);
        };
        @RegisterExtension
        @Order(2)
        static final RuntimeExtension RUNTIME = ComponentRuntimeExtension.Builder.newInstance()
                .name("cdc-agent")
                .modules(":dist:bom:virtual-controlplane-cdc-base-bom",
                        ":dist:bom:virtual-controlplane-feature-nats-cdc-bom")
                .endpoints(ENDPOINTS.build())
                .configurationProvider(RuntimeSmokeTests::config)
                .configurationProvider(CdcAgent::config)
                .configurationProvider(NATS_EXTENSION::configFor)
                .configurationProvider(CdcAgent::pgConfig)
                .paramProvider(DefaultEndpoint.class, RuntimeSmokeTests::defaultEndpoint)
                .build();

        private static Config pgConfig() {
            var cfg = POSTGRESQL_EXTENSION.configFor(DB_NAME);
            var override = new HashMap<>(cfg.getEntries());
            override.put("edc.sql.schema.autocreate", "false");
            return ConfigFactory.fromMap(override);
        }

        private static Config config() {
            return ConfigFactory.fromMap(new HashMap<>() {
                {
                    put("edc.postgres.cdc.url", POSTGRESQL_EXTENSION.getJdbcUrl(DB_NAME));
                    put("edc.postgres.cdc.user", POSTGRESQL_EXTENSION.getUsername());
                    put("edc.postgres.cdc.password", POSTGRESQL_EXTENSION.getPassword());
                    put("edc.postgres.cdc.slot", "edc_cdc_slot_" + Runtimes.ControlPlane.NAME.toLowerCase());
                }
            });
        }
    }

}
