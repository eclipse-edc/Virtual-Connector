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

package org.eclipse.edc.virtualized;

import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.annotations.PostgresqlIntegrationTest;
import org.eclipse.edc.junit.extensions.EmbeddedRuntime;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimePerMethodExtension;
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

import java.util.HashMap;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static java.lang.String.format;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.hamcrest.Matchers.equalTo;

public class RuntimeSmokeTests {
    abstract static class SmokeTest {
        public static final String DEFAULT_PORT = "8080";
        public static final String DEFAULT_PATH = "/api";

        @Test
        void assertRuntimeReady() {
            await().untilAsserted(() -> given()
                    .baseUri("http://localhost:" + DEFAULT_PORT + DEFAULT_PATH + "/check/startup")
                    .get()
                    .then()
                    .statusCode(200)
                    .log().ifValidationFails()
                    .body("isSystemHealthy", equalTo(true)));

        }
    }

    @Nested
    @EndToEndTest
    class ControlPlaneMemoryDcp extends SmokeTest {

        @RegisterExtension
        protected RuntimeExtension runtime =
                new RuntimePerMethodExtension(new EmbeddedRuntime("control-plane-memory",
                        ":system-tests:runtimes:controlplane-memory"
                ).configurationProvider(ControlPlaneMemoryDcp::runtimeConfiguration)
                );


        private static Config runtimeConfiguration() {
            return ConfigFactory.fromMap(new HashMap<>() {
                {
                    put("edc.iam.sts.oauth.token.url", "https://sts.com/token");
                    put("edc.iam.sts.oauth.client.id", "test-client");
                    put("edc.iam.sts.oauth.client.secret.alias", "test-alias");
                    put("web.http.port", DEFAULT_PORT);
                    put("web.http.path", DEFAULT_PATH);
                    put("web.http.version.port", String.valueOf(getFreePort()));
                    put("web.http.version.path", "/api/version");
                    put("web.http.control.port", String.valueOf(getFreePort()));
                    put("web.http.control.path", "/api/control");
                    put("web.http.management.port", "8081");
                    put("web.http.management.path", "/api/management");
                    put("edc.iam.sts.privatekey.alias", "privatekey");
                    put("edc.iam.sts.publickey.id", "publickey");
                    put("edc.iam.issuer.id", "did:web:someone");
                }
            });
        }
    }

    @Nested
    @PostgresqlIntegrationTest
    @Testcontainers
    class ControlPlanePgDcp extends SmokeTest {

        static final String DOCKER_IMAGE_NAME = "hashicorp/vault:1.18.3";
        static final String TOKEN = UUID.randomUUID().toString();

        @Order(0)
        @RegisterExtension
        static final PostgresqlEndToEndExtension POSTGRESQL_EXTENSION = new PostgresqlEndToEndExtension();
        static final String DB_NAME = "smoke_test";


        @Order(1)
        @RegisterExtension
        static final BeforeAllCallback CREATE_DATABASES = context -> {
            POSTGRESQL_EXTENSION.createDatabase(DB_NAME);
        };

        @Container
        private static final VaultContainer<?> VAULTCONTAINER = new VaultContainer<>(DOCKER_IMAGE_NAME)
                .withVaultToken(TOKEN);

        @RegisterExtension
        @Order(2)
        protected RuntimeExtension runtime = new RuntimePerMethodExtension(new EmbeddedRuntime("control-plane-pg",
                ":system-tests:runtimes:controlplane-postgres"
        ).configurationProvider(ControlPlanePgDcp::runtimeConfiguration)
                .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(DB_NAME))
        );

        private static Config runtimeConfiguration() {
            return ConfigFactory.fromMap(new HashMap<>() {
                {
                    put("edc.iam.sts.oauth.token.url", "https://sts.com/token");
                    put("edc.iam.sts.oauth.client.id", "test-client");
                    put("edc.iam.sts.oauth.client.secret.alias", "test-alias");
                    put("web.http.port", DEFAULT_PORT);
                    put("web.http.path", DEFAULT_PATH);
                    put("web.http.version.port", String.valueOf(getFreePort()));
                    put("web.http.version.path", "/api/version");
                    put("web.http.control.port", String.valueOf(getFreePort()));
                    put("web.http.control.path", "/api/control");
                    put("web.http.management.port", "8081");
                    put("web.http.management.path", "/api/management");
                    put("edc.iam.sts.privatekey.alias", "privatekey");
                    put("edc.iam.sts.publickey.id", "publickey");
                    put("edc.iam.issuer.id", "did:web:someone");
                    put("edc.vault.hashicorp.url", format("http://localhost:%s", getPort()));
                    put("edc.vault.hashicorp.token", TOKEN);
                }
            });
        }

        private static Integer getPort() {
            if (!VAULTCONTAINER.isRunning()) {
                VAULTCONTAINER.start();
                VAULTCONTAINER.waitingFor(Wait.forHealthcheck());
            }
            return VAULTCONTAINER.getFirstMappedPort();
        }
    }

}
