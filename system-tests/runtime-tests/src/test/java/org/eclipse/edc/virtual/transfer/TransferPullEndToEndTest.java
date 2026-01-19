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

package org.eclipse.edc.virtual.transfer;

import org.eclipse.edc.api.authentication.OauthServerEndToEndExtension;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.annotations.PostgresqlIntegrationTest;
import org.eclipse.edc.junit.extensions.ComponentRuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndExtension;
import org.eclipse.edc.virtual.Runtimes;
import org.eclipse.edc.virtual.nats.testfixtures.NatsEndToEndExtension;
import org.eclipse.edc.virtual.transfer.fixtures.Participants;
import org.eclipse.edc.virtual.transfer.fixtures.VirtualConnector;
import org.eclipse.edc.virtual.transfer.fixtures.VirtualConnectorClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;

import static org.eclipse.edc.virtual.test.system.fixtures.DockerImages.createPgContainer;


class TransferPullEndToEndTest {

    public static final String PROVIDER_CONTEXT = "provider";
    public static final String CONSUMER_CONTEXT = "consumer";
    public static final String PROVIDER_ID = "provider-id";
    public static final String CONSUMER_ID = "consumer-id";

    private static Participants participants() {
        return new Participants(
                new Participants.Participant(PROVIDER_CONTEXT, PROVIDER_ID),
                new Participants.Participant(CONSUMER_CONTEXT, CONSUMER_ID)
        );
    }

    @Nested
    @EndToEndTest
    class InMemory extends TransferPullEndToEndTestBase {

        @Order(0)
        @RegisterExtension
        static final OauthServerEndToEndExtension AUTH_SERVER_EXTENSION = OauthServerEndToEndExtension.Builder.newInstance().build();

        @RegisterExtension
        static final RuntimeExtension CONTROL_PLANE = ComponentRuntimeExtension.Builder.newInstance()
                .name(Runtimes.ControlPlane.NAME)
                .modules(Runtimes.ControlPlane.MODULES)
                .endpoints(Runtimes.ControlPlane.ENDPOINTS.build())
                .configurationProvider(Runtimes.ControlPlane::config)
                .configurationProvider(AUTH_SERVER_EXTENSION::getConfig)
                .paramProvider(VirtualConnector.class, VirtualConnector::forContext)
                .paramProvider(VirtualConnectorClient.class, (ctx) -> VirtualConnectorClient.forContext(ctx, AUTH_SERVER_EXTENSION.getAuthServer()))
                .paramProvider(Participants.class, context -> participants())
                .build();

    }

    @Nested
    @EndToEndTest
    class InMemoryTasks extends TransferPullEndToEndTestBase {

        @Order(0)
        @RegisterExtension
        static final OauthServerEndToEndExtension AUTH_SERVER_EXTENSION = OauthServerEndToEndExtension.Builder.newInstance().build();

        @RegisterExtension
        static final RuntimeExtension CONTROL_PLANE = ComponentRuntimeExtension.Builder.newInstance()
                .name(Runtimes.ControlPlane.NAME)
                .modules(Runtimes.ControlPlane.MEMORY_TASKS)
                .endpoints(Runtimes.ControlPlane.ENDPOINTS.build())
                .configurationProvider(Runtimes.ControlPlane::config)
                .configurationProvider(AUTH_SERVER_EXTENSION::getConfig)
                .paramProvider(VirtualConnector.class, VirtualConnector::forContext)
                .paramProvider(VirtualConnectorClient.class, (ctx) -> VirtualConnectorClient.forContext(ctx, AUTH_SERVER_EXTENSION.getAuthServer()))
                .paramProvider(Participants.class, context -> participants())
                .build();

    }

    @Nested
    @PostgresqlIntegrationTest
    class Postgres extends TransferPullEndToEndTestBase {

        @Order(0)
        @RegisterExtension
        static final OauthServerEndToEndExtension AUTH_SERVER_EXTENSION = OauthServerEndToEndExtension.Builder.newInstance().build();

        @Order(0)
        @RegisterExtension
        static final NatsEndToEndExtension NATS_EXTENSION = new NatsEndToEndExtension();
        @Order(0)
        @RegisterExtension
        static final PostgresqlEndToEndExtension POSTGRESQL_EXTENSION = new PostgresqlEndToEndExtension(createPgContainer());
        @Order(1)
        @RegisterExtension
        static final BeforeAllCallback SETUP = context -> {
            POSTGRESQL_EXTENSION.createDatabase(Runtimes.ControlPlane.NAME.toLowerCase());
        };
        @Order(2)
        @RegisterExtension
        static final RuntimeExtension CONTROL_PLANE = ComponentRuntimeExtension.Builder.newInstance()
                .name(Runtimes.ControlPlane.NAME)
                .modules(Runtimes.ControlPlane.PG_MODULES)
                .endpoints(Runtimes.ControlPlane.ENDPOINTS.build())
                .configurationProvider(Runtimes.ControlPlane::config)
                .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(Runtimes.ControlPlane.NAME.toLowerCase()))
                .configurationProvider(NATS_EXTENSION::configFor)
                .configurationProvider(Postgres::runtimeConfiguration)
                .configurationProvider(AUTH_SERVER_EXTENSION::getConfig)
                .paramProvider(VirtualConnector.class, VirtualConnector::forContext)
                .paramProvider(VirtualConnectorClient.class, (ctx) -> VirtualConnectorClient.forContext(ctx, AUTH_SERVER_EXTENSION.getAuthServer()))
                .paramProvider(Participants.class, context -> participants())
                .build();

        private static Config runtimeConfiguration() {
            return ConfigFactory.fromMap(new HashMap<>() {
                {
                    put("edc.postgres.cdc.url", POSTGRESQL_EXTENSION.getJdbcUrl(Runtimes.ControlPlane.NAME.toLowerCase()));
                    put("edc.postgres.cdc.user", POSTGRESQL_EXTENSION.getUsername());
                    put("edc.postgres.cdc.password", POSTGRESQL_EXTENSION.getPassword());
                    put("edc.postgres.cdc.slot", "edc_cdc_slot_" + Runtimes.ControlPlane.NAME.toLowerCase());
                }
            });
        }
    }

    @Nested
    @PostgresqlIntegrationTest
    class PostgresNatsTasks extends TransferPullEndToEndTestBase {

        @Order(0)
        @RegisterExtension
        static final OauthServerEndToEndExtension AUTH_SERVER_EXTENSION = OauthServerEndToEndExtension.Builder.newInstance().build();

        @Order(0)
        @RegisterExtension
        static final NatsEndToEndExtension NATS_EXTENSION = new NatsEndToEndExtension();
        @Order(0)
        @RegisterExtension
        static final PostgresqlEndToEndExtension POSTGRESQL_EXTENSION = new PostgresqlEndToEndExtension(createPgContainer());
        @Order(1)
        @RegisterExtension
        static final BeforeAllCallback SETUP = context -> {
            POSTGRESQL_EXTENSION.createDatabase(Runtimes.ControlPlane.NAME.toLowerCase());
        };

        @Order(2)
        @RegisterExtension
        static final RuntimeExtension CONTROL_PLANE = ComponentRuntimeExtension.Builder.newInstance()
                .name(Runtimes.ControlPlane.NAME)
                .modules(Runtimes.ControlPlane.PG_NATS_TASKS_MODULES)
                .endpoints(Runtimes.ControlPlane.ENDPOINTS.build())
                .configurationProvider(Runtimes.ControlPlane::config)
                .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(Runtimes.ControlPlane.NAME.toLowerCase()))
                .configurationProvider(NATS_EXTENSION::configFor)
                .configurationProvider(AUTH_SERVER_EXTENSION::getConfig)
                .paramProvider(VirtualConnector.class, VirtualConnector::forContext)
                .paramProvider(VirtualConnectorClient.class, (ctx) -> VirtualConnectorClient.forContext(ctx, AUTH_SERVER_EXTENSION.getAuthServer()))
                .paramProvider(Participants.class, context -> participants())
                .build();

    }

    @Nested
    @PostgresqlIntegrationTest
    class PostgresTasks extends TransferPullEndToEndTestBase {

        @Order(0)
        @RegisterExtension
        static final OauthServerEndToEndExtension AUTH_SERVER_EXTENSION = OauthServerEndToEndExtension.Builder.newInstance().build();


        @Order(0)
        @RegisterExtension
        static final PostgresqlEndToEndExtension POSTGRESQL_EXTENSION = new PostgresqlEndToEndExtension(createPgContainer());
        @Order(1)
        @RegisterExtension
        static final BeforeAllCallback SETUP = context -> {
            POSTGRESQL_EXTENSION.createDatabase(Runtimes.ControlPlane.NAME.toLowerCase());
        };
        @Order(2)
        @RegisterExtension
        static final RuntimeExtension CONTROL_PLANE = ComponentRuntimeExtension.Builder.newInstance()
                .name(Runtimes.ControlPlane.NAME)
                .modules(Runtimes.ControlPlane.PG_TASKS_MODULES)
                .endpoints(Runtimes.ControlPlane.ENDPOINTS.build())
                .configurationProvider(Runtimes.ControlPlane::config)
                .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(Runtimes.ControlPlane.NAME.toLowerCase()))
                .configurationProvider(AUTH_SERVER_EXTENSION::getConfig)
                .paramProvider(VirtualConnector.class, VirtualConnector::forContext)
                .paramProvider(VirtualConnectorClient.class, (ctx) -> VirtualConnectorClient.forContext(ctx, AUTH_SERVER_EXTENSION.getAuthServer()))
                .paramProvider(Participants.class, context -> participants())
                .build();

    }

}
