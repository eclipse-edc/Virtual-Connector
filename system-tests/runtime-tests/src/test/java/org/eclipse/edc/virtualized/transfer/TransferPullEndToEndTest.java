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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.dataplane.spi.Endpoint;
import org.eclipse.edc.connector.dataplane.spi.iam.PublicEndpointGeneratorService;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.extensions.ComponentRuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndExtension;
import org.eclipse.edc.virtualized.Runtimes;
import org.eclipse.edc.virtualized.nats.testfixtures.NatsEndToEndExtension;
import org.eclipse.edc.virtualized.transfer.fixtures.VirtualConnector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.virtualized.test.system.fixtures.DockerImages.createPgContainer;


class TransferPullEndToEndTest {

    abstract static class Tests {

        public static final String PROVIDER_CONTEXT = "provider";
        public static final String CONSUMER_CONTEXT = "consumer";
        public static final String PROVIDER_ID = "provider-id";
        public static final String CONSUMER_ID = "consumer-id";
        private static final String ASSET_ID = "asset-id";
        private static final String POLICY_ID = "policy-id";
        @RegisterExtension
        static WireMockExtension callbacksEndpoint = WireMockExtension.newInstance()
                .options(wireMockConfig().dynamicPort())
                .build();

        @BeforeAll
        static void beforeAll(PublicEndpointGeneratorService generatorService, Vault vault) {

            generatorService.addGeneratorFunction("HttpData", address -> Endpoint.url("http://example.com"));

            try {
                var key = new ECKeyGenerator(Curve.P_256)
                        .keyID("sign-key")
                        .generate();
                vault.storeSecret("private-key", key.toJSONString());
                vault.storeSecret("public-key", key.toPublicJWK().toJSONString());
            } catch (JOSEException e) {
                throw new RuntimeException(e);
            }

        }

        @Test
        void httpPull_dataTransfer(VirtualConnector env, TransferProcessService transferService) {
            var providerAddress = env.getProtocolEndpoint().get() + "/" + PROVIDER_CONTEXT + "/2025-1";

            var assetId = setup(env);
            var transferProcessId = env.startTransfer(CONSUMER_CONTEXT, providerAddress, PROVIDER_ID, assetId, "HttpData-PULL");

            var consumerTransfer = transferService.findById(transferProcessId);
            assertThat(consumerTransfer).isNotNull();

            var providerTransfer = transferService.findById(consumerTransfer.getCorrelationId());

            assertThat(providerTransfer).isNotNull();

            assertThat(consumerTransfer.getParticipantContextId()).isEqualTo(CONSUMER_CONTEXT);
            assertThat(providerTransfer.getParticipantContextId()).isEqualTo(PROVIDER_CONTEXT);

        }

        private String setup(VirtualConnector env) {
            var asset = Asset.Builder.newInstance()
                    .id(ASSET_ID)
                    .dataAddress(DataAddress.Builder.newInstance().type("HttpData").build())
                    .participantContextId(PROVIDER_CONTEXT)
                    .build();

            var policyDefinition = PolicyDefinition.Builder.newInstance()
                    .id(POLICY_ID)
                    .policy(Policy.Builder.newInstance().build())
                    .participantContextId(PROVIDER_CONTEXT)
                    .build();

            env.createParticipant(CONSUMER_CONTEXT, CONSUMER_ID);
            env.createParticipant(PROVIDER_CONTEXT, PROVIDER_ID);
            env.setupResources(PROVIDER_CONTEXT, asset, policyDefinition, policyDefinition);

            return asset.getId();
        }

    }

    @Nested
    @EndToEndTest
    class InMemory extends Tests {

        @RegisterExtension
        static final RuntimeExtension CONTROL_PLANE = ComponentRuntimeExtension.Builder.newInstance()
                .name(Runtimes.ControlPlane.NAME)
                .modules(Runtimes.ControlPlane.MODULES)
                .endpoints(Runtimes.ControlPlane.ENDPOINTS.build())
                .configurationProvider(Runtimes.ControlPlane::config)
                .paramProvider(VirtualConnector.class, Runtimes.ControlPlane::connector)
                .build();
    }


    @Nested
    @EndToEndTest
    class Postgres extends Tests {

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
            NATS_EXTENSION.createStream("state_machine", "negotiations.>", "transfers.>");
            NATS_EXTENSION.createConsumer("state_machine", "cn-subscriber", "negotiations.>");
            NATS_EXTENSION.createConsumer("state_machine", "tp-subscriber", "transfers.>");
        };

        @Order(2)
        @RegisterExtension
        static final RuntimeExtension CONTROL_PLANE = ComponentRuntimeExtension.Builder.newInstance()
                .name(Runtimes.ControlPlane.NAME)
                .modules(Runtimes.ControlPlane.PG_MODULES)
                .endpoints(Runtimes.ControlPlane.ENDPOINTS.build())
                .configurationProvider(Runtimes.ControlPlane::config)
                .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(Runtimes.ControlPlane.NAME.toLowerCase()))
                .configurationProvider(Postgres::runtimeConfiguration)
                .paramProvider(VirtualConnector.class, Runtimes.ControlPlane::connector)
                .build();

        @Order(3)
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
