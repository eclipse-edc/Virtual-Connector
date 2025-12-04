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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.vault.VaultContainer;

import java.net.URI;
import java.util.HashMap;

import static io.restassured.RestAssured.given;
import static java.lang.String.format;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.util.io.Ports.getFreePort;
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
                .modules(":system-tests:runtimes:controlplane-memory")
                .endpoints(ENDPOINTS.build())
                .configurationProvider(RuntimeSmokeTests::config)
                .paramProvider(DefaultEndpoint.class, RuntimeSmokeTests::defaultEndpoint)
                .build();


    }

    @Nested
    @PostgresqlIntegrationTest
    @Testcontainers
    class ControlPlanePgDcp extends SmokeTest {

        static final String DOCKER_IMAGE_NAME = "hashicorp/vault:1.18.3";
        static final String TOKEN = "token";

        @Order(0)
        @RegisterExtension
        static final PostgresqlEndToEndExtension POSTGRESQL_EXTENSION = new PostgresqlEndToEndExtension();
        static final String DB_NAME = "smoke_test";


        @Order(1)
        @RegisterExtension
        static final BeforeAllCallback CREATE_DATABASES = context -> {
            POSTGRESQL_EXTENSION.createDatabase(DB_NAME);
        };

        @SuppressWarnings("resource")
        @Container
        private static final VaultContainer<?> VAULTCONTAINER = new VaultContainer<>(DOCKER_IMAGE_NAME)
                .withVaultToken(TOKEN);

        @RegisterExtension
        @Order(2)
        static final RuntimeExtension RUNTIME = ComponentRuntimeExtension.Builder.newInstance()
                .name("control-plane-pg")
                .modules(":system-tests:runtimes:controlplane-postgres")
                .endpoints(ENDPOINTS.build())
                .configurationProvider(RuntimeSmokeTests::config)
                .configurationProvider(ControlPlanePgDcp::config)
                .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(DB_NAME))
                .paramProvider(DefaultEndpoint.class, RuntimeSmokeTests::defaultEndpoint)
                .build();

        private static Config config() {
            return ConfigFactory.fromMap(new HashMap<>() {
                {
                    put("edc.vault.hashicorp.url", format("http://localhost:%s", getVaultPort()));
                    put("edc.vault.hashicorp.token", TOKEN);
                    put("edc.iam.oauth2.issuer", "test-issuer");
                    put("edc.iam.oauth2.jwks.cache.validity", "0");
                    put("edc.iam.oauth2.jwks.url", "https://example.com/jwks");
                }
            });
        }

        private static Integer getVaultPort() {
            if (!VAULTCONTAINER.isRunning()) {
                VAULTCONTAINER.start();
                VAULTCONTAINER.waitingFor(Wait.forHealthcheck());
            }
            return VAULTCONTAINER.getFirstMappedPort();
        }
    }

}
