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

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.identityhub.tests.fixtures.DefaultRuntimes;
import org.eclipse.edc.identityhub.tests.fixtures.credentialservice.IdentityHub;
import org.eclipse.edc.identityhub.tests.fixtures.credentialservice.IdentityHubApiClient;
import org.eclipse.edc.identityhub.tests.fixtures.issuerservice.IssuerService;
import org.eclipse.edc.junit.annotations.PostgresqlIntegrationTest;
import org.eclipse.edc.junit.extensions.ComponentRuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.utils.Endpoints;
import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndExtension;
import org.eclipse.edc.virtualized.Runtimes;
import org.eclipse.edc.virtualized.extensions.DcpPatchExtension;
import org.eclipse.edc.virtualized.nats.testfixtures.NatsEndToEndExtension;
import org.eclipse.edc.virtualized.policy.cel.model.CelExpression;
import org.eclipse.edc.virtualized.policy.cel.service.CelPolicyExpressionService;
import org.eclipse.edc.virtualized.transfer.fixtures.Participants;
import org.eclipse.edc.virtualized.transfer.fixtures.VirtualConnector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;
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

            var providerResponse = setupParticipant(identityHub, connector, issuerDid, participants.provider().contextId(), providerHolderDid);
            var consumerResponse = setupParticipant(identityHub, connector, issuerDid, participants.consumer().contextId(), consumerHolderDid);

            var providerPid = hubApiClient.requestCredential(providerResponse.apiKey(), providerHolderDid, issuerDid, "credential-id", "MembershipCredential");
            var consumerPid = hubApiClient.requestCredential(consumerResponse.apiKey(), consumerHolderDid, issuerDid, "credential-id", "MembershipCredential");

            identityHub.waitForCredentialIssuer(providerPid, providerHolderDid);
            identityHub.waitForCredentialIssuer(consumerPid, consumerHolderDid);

        }


        @Test
        void httpPull_dataTransfer_withMembershipExpression(VirtualConnector env, Participants participants,
                                                            TransferProcessService transferService,
                                                            CelPolicyExpressionService celPolicyExpressionService) {

            var leftOperand = "https://w3id.org/example/credentials/MembershipCredential";
            var expression = """
                    ctx.agent.claims.vc
                    .exists(c, c.type.exists(t, t == 'MembershipCredential'))
                    """;

            var providerAddress = env.getProtocolEndpoint().get() + "/" + participants.provider().contextId() + "/2025-1";
            var expr = CelExpression.Builder.newInstance().id("id")
                    .leftOperand(leftOperand)
                    .expression(expression)
                    .description("membership expression")
                    .build();
            celPolicyExpressionService.create(expr)
                    .orElseThrow(f -> new RuntimeException("Failed to store CEL expression: " + f.getFailureDetail()));

            var policy = Policy.Builder.newInstance()
                    .permission(Permission.Builder
                            .newInstance()
                            .action(Action.Builder.newInstance().type(ODRL_SCHEMA + "use").build())
                            .constraint(AtomicConstraint.Builder.newInstance()
                                    .leftExpression(new LiteralExpression(leftOperand))
                                    .operator(Operator.EQ)
                                    .rightExpression(new LiteralExpression("active"))
                                    .build())
                            .build())
                    .build();
            var assetId = setup(env, participants.provider(), policy);
            var transferProcessId = env.startTransfer(participants.consumer().contextId(), providerAddress, participants.provider().id(), assetId, "HttpData-PULL", policy);

            var consumerTransfer = transferService.findById(transferProcessId);
            assertThat(consumerTransfer).isNotNull();

            var providerTransfer = transferService.findById(consumerTransfer.getCorrelationId());

            assertThat(providerTransfer).isNotNull();

            assertThat(consumerTransfer.getParticipantContextId()).isEqualTo(participants.consumer().contextId());
            assertThat(providerTransfer.getParticipantContextId()).isEqualTo(participants.provider().contextId());

        }

        private String setup(VirtualConnector env, Participants.Participant provider, Policy policy) {

            var asset = Asset.Builder.newInstance()
                    .id(UUID.randomUUID().toString())
                    .dataAddress(DataAddress.Builder.newInstance().type("HttpData").build())
                    .participantContextId(provider.contextId())
                    .build();

            var policyDefinition = PolicyDefinition.Builder.newInstance()
                    .id(UUID.randomUUID().toString())
                    .policy(policy)
                    .participantContextId(provider.contextId())
                    .build();

            env.setupResources(provider.contextId(), asset, policyDefinition, policyDefinition);

            return asset.getId();
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
