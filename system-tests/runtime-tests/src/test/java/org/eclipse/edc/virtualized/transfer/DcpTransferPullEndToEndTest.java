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

package org.eclipse.edc.virtualized.transfer;

import org.eclipse.edc.identityhub.tests.fixtures.DefaultRuntimes;
import org.eclipse.edc.identityhub.tests.fixtures.credentialservice.IdentityHub;
import org.eclipse.edc.identityhub.tests.fixtures.credentialservice.IdentityHubApiClient;
import org.eclipse.edc.identityhub.tests.fixtures.issuerservice.IssuerService;
import org.eclipse.edc.junit.annotations.PostgresqlIntegrationTest;
import org.eclipse.edc.junit.extensions.ComponentRuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.utils.Endpoints;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndExtension;
import org.eclipse.edc.virtualized.Runtimes;
import org.eclipse.edc.virtualized.extensions.DcpPatchExtension;
import org.eclipse.edc.virtualized.nats.testfixtures.NatsEndToEndExtension;
import org.eclipse.edc.virtualized.transfer.fixtures.Participants;
import org.eclipse.edc.virtualized.transfer.fixtures.VirtualConnector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.Map;

import static org.eclipse.edc.virtualized.test.system.fixtures.DockerImages.createPgContainer;
import static org.eclipse.edc.virtualized.transfer.fixtures.TestFunction.setupHolder;
import static org.eclipse.edc.virtualized.transfer.fixtures.TestFunction.setupIssuer;
import static org.eclipse.edc.virtualized.transfer.fixtures.TestFunction.setupParticipant;


class DcpTransferPullEndToEndTest {

    public static final String PROVIDER_CONTEXT = "provider";
    public static final String CONSUMER_CONTEXT = "consumer";

    private static Participants participants(Endpoints endpoints) {
        var providerDid = Runtimes.IdentityHub.didFor(endpoints, PROVIDER_CONTEXT);
        var providerCfg = Runtimes.IdentityHub.dcpConfig(endpoints, PROVIDER_CONTEXT);
        var consumerDid = Runtimes.IdentityHub.didFor(endpoints, CONSUMER_CONTEXT);
        var consumerCfg = Runtimes.IdentityHub.dcpConfig(endpoints, CONSUMER_CONTEXT);
        return new Participants(
                new Participants.Participant(PROVIDER_CONTEXT, providerDid, providerCfg.getEntries()),
                new Participants.Participant(CONSUMER_CONTEXT, consumerDid, consumerCfg.getEntries())
        );
    }

    @SuppressWarnings("JUnitMalformedDeclaration")
    abstract static class DcpTransferPullEndToEndTestBase extends TransferPullEndToEndTestBase {

        /**
         * Set up the test environment by creating one issuer, two participants in their
         * respective Identity Hubs, and issuing a MembershipCredential credential for each participant.
         */
        @BeforeAll
        static void setup(IssuerService issuer,
                          IdentityHub identityHub,
                          IdentityHubApiClient hubApiClient,
                          VirtualConnector connector,
                          Participants participants) {


            var consumerHolderDid = participants.consumer().id();
            var providerHolderDid = participants.provider().id();
            var issuerDid = issuer.didFor(Runtimes.Issuer.NAME);

            setupIssuer(issuer, Runtimes.Issuer.NAME, issuerDid);

            setupHolder(issuer, Runtimes.Issuer.NAME, consumerHolderDid);
            setupHolder(issuer, Runtimes.Issuer.NAME, providerHolderDid);

            var providerResponse = setupParticipant(identityHub, connector, issuerDid, providerHolderDid);
            var consumerResponse = setupParticipant(identityHub, connector, issuerDid, consumerHolderDid);

            var providerPid = hubApiClient.requestCredential(providerResponse.apiKey(), providerHolderDid, issuerDid, "credential-id", "MembershipCredential");
            var consumerPid = hubApiClient.requestCredential(consumerResponse.apiKey(), consumerHolderDid, issuerDid, "credential-id", "MembershipCredential");

            identityHub.waitForCredentialIssuer(providerPid, providerHolderDid);
            identityHub.waitForCredentialIssuer(consumerPid, consumerHolderDid);

        }
    }

    @Nested
    @PostgresqlIntegrationTest
    class Postgres extends DcpTransferPullEndToEndTestBase {

        @Order(0)
        @RegisterExtension
        static final NatsEndToEndExtension NATS_EXTENSION = new NatsEndToEndExtension();

        @Order(0)
        @RegisterExtension
        static final PostgresqlEndToEndExtension POSTGRESQL_EXTENSION = new PostgresqlEndToEndExtension(createPgContainer());

        @Order(1)
        @RegisterExtension
        static final BeforeAllCallback SETUP = context -> {
            POSTGRESQL_EXTENSION.createDatabase(Runtimes.Issuer.NAME.toLowerCase());
            POSTGRESQL_EXTENSION.createDatabase(Runtimes.IdentityHub.NAME.toLowerCase());
            POSTGRESQL_EXTENSION.createDatabase(Runtimes.ControlPlane.NAME.toLowerCase());
            NATS_EXTENSION.createStream("state_machine", "negotiations.>", "transfers.>");
            NATS_EXTENSION.createConsumer("state_machine", "cn-subscriber", "negotiations.>");
            NATS_EXTENSION.createConsumer("state_machine", "tp-subscriber", "transfers.>");
        };


        @Order(2)
        @RegisterExtension
        static final RuntimeExtension ISSUER = ComponentRuntimeExtension.Builder.newInstance()
                .name(Runtimes.Issuer.NAME)
                .modules(Runtimes.Issuer.MODULES)
                .endpoints(DefaultRuntimes.Issuer.ENDPOINTS.build())
                .configurationProvider(DefaultRuntimes.Issuer::config)
                .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(Runtimes.Issuer.NAME.toLowerCase()))
                .paramProvider(IssuerService.class, IssuerService::forContext)
                .build();

        static final Endpoints IDENTITY_HUB_ENDPOINTS = DefaultRuntimes.IdentityHub.ENDPOINTS.build();

        @Order(2)
        @RegisterExtension
        static final RuntimeExtension IDENTITY_HUB = ComponentRuntimeExtension.Builder.newInstance()
                .name(Runtimes.IdentityHub.NAME)
                .modules(Runtimes.IdentityHub.MODULES)
                .endpoints(IDENTITY_HUB_ENDPOINTS)
                .configurationProvider(DefaultRuntimes.IdentityHub::config)
                .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(Runtimes.IdentityHub.NAME.toLowerCase()))
                .configurationProvider(() -> ConfigFactory.fromMap(Map.of("edc.iam.credential.status.check.period", "0")))
                .paramProvider(IdentityHub.class, IdentityHub::forContext)
                .paramProvider(IdentityHubApiClient.class, IdentityHubApiClient::forContext)
                .build();


        @Order(3)
        @RegisterExtension
        static final RuntimeExtension CONTROL_PLANE = ComponentRuntimeExtension.Builder.newInstance()
                .name(Runtimes.ControlPlane.NAME)
                .modules(Runtimes.ControlPlane.DCP_PG_MODULES)
                .endpoints(Runtimes.ControlPlane.ENDPOINTS.build())
                .configurationProvider(Runtimes.ControlPlane::config)
                .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(Runtimes.ControlPlane.NAME.toLowerCase()))
                .configurationProvider(Postgres::runtimeConfiguration)
                .configurationProvider(() -> ConfigFactory.fromMap(Map.of("edc.iam.did.web.use.https", "false")))
                .paramProvider(VirtualConnector.class, VirtualConnector::forContext)
                .paramProvider(Participants.class, context -> participants(IDENTITY_HUB_ENDPOINTS))
                .build()
                .registerSystemExtension(ServiceExtension.class, new DcpPatchExtension());

        @Order(4)
        @RegisterExtension
        static final BeforeAllCallback SEED = context -> {
            POSTGRESQL_EXTENSION.execute(Runtimes.ControlPlane.NAME.toLowerCase(), "ALTER TABLE edc_contract_negotiation REPLICA IDENTITY FULL;");
            POSTGRESQL_EXTENSION.execute(Runtimes.ControlPlane.NAME.toLowerCase(), "ALTER TABLE edc_transfer_process REPLICA IDENTITY FULL;");
        };

        private static Config runtimeConfiguration() {
            return ConfigFactory.fromMap(new HashMap<>() {
                {
                    put("edc.postgres.cdc.url", POSTGRESQL_EXTENSION.getJdbcUrl(Runtimes.ControlPlane.NAME.toLowerCase()));
                    put("edc.postgres.cdc.user", POSTGRESQL_EXTENSION.getUsername());
                    put("edc.postgres.cdc.password", POSTGRESQL_EXTENSION.getPassword());
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
