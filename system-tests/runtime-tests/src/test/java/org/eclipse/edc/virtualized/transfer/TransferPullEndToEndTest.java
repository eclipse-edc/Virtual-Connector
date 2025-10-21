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
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndExtension;
import org.eclipse.edc.virtualized.fixtures.ControlPlaneExtension;
import org.eclipse.edc.virtualized.fixtures.ControlPlaneRuntime;
import org.eclipse.edc.virtualized.nats.testfixtures.NatsEndToEndExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.virtualized.transfer.TestFunctions.CONSUMER_CONTEXT;
import static org.eclipse.edc.virtualized.transfer.TestFunctions.CONSUMER_ID;
import static org.eclipse.edc.virtualized.transfer.TestFunctions.PROVIDER_CONTEXT;
import static org.eclipse.edc.virtualized.transfer.TestFunctions.PROVIDER_ID;
import static org.eclipse.edc.virtualized.transfer.TestFunctions.setupControlPlane;
import static org.eclipse.edc.virtualized.transfer.TestFunctions.startTransfer;


class TransferPullEndToEndTest {

    abstract static class Tests {


        private static final String ASSET_ID = "asset-id";
        private static final String POLICY_ID = "policy-id";
        @RegisterExtension
        static WireMockExtension callbacksEndpoint = WireMockExtension.newInstance()
                .options(wireMockConfig().dynamicPort())
                .build();

        @BeforeAll
        static void beforeAll(ControlPlaneRuntime cp, PublicEndpointGeneratorService generatorService,
                              Vault vault, ParticipantContextConfigService config) {

            generatorService.addGeneratorFunction("HttpData", address -> Endpoint.url("http://example.com"));

            config.save(PROVIDER_CONTEXT, ConfigFactory.fromMap(Map.of("edc.participant.id", PROVIDER_ID))).orElseThrow(e -> new RuntimeException(e.getFailureDetail()));
            config.save(CONSUMER_CONTEXT, ConfigFactory.fromMap(Map.of("edc.participant.id", CONSUMER_ID))).orElseThrow(e -> new RuntimeException(e.getFailureDetail()));

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
        void httpPull_dataTransfer(ControlPlaneRuntime cp, TransferProcessService transferProcessService) throws IOException {
            var assetId = setup(cp);
            var transferProcessId = startTransfer(cp, assetId, "HttpData-PULL");
            var transferService = cp.getService(TransferProcessService.class);

            var consumerTransfer = transferService.findById(transferProcessId);
            assertThat(consumerTransfer).isNotNull();

            var providerTransfer = transferService.findById(consumerTransfer.getCorrelationId());

            assertThat(providerTransfer).isNotNull();


            assertThat(consumerTransfer.getParticipantContextId()).isEqualTo(CONSUMER_CONTEXT);
            assertThat(providerTransfer.getParticipantContextId()).isEqualTo(PROVIDER_CONTEXT);

        }

        private String setup(ControlPlaneRuntime cp) {
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
            setupControlPlane(cp, asset, policyDefinition);

            return asset.getId();
        }

    }

    @Nested
    @EndToEndTest
    class InMemory extends Tests {

        @RegisterExtension
        static final ControlPlaneExtension CONTROL_PLANE = ControlPlaneExtension.Builder.newInstance()
                .id("controlplane")
                .name("controlplane")
                .modules(":system-tests:runtimes:e2e:e2e-controlplane-memory")
                .build();
    }


    @Nested
    @EndToEndTest
    class Postgres extends Tests {

        protected static final String CONNECTOR = "connector";

        @Order(0)
        @RegisterExtension
        static final NatsEndToEndExtension NATS_EXTENSION = new NatsEndToEndExtension();

        static final ImageFromDockerfile BASE_IMAGE = new ImageFromDockerfile()
                .withDockerfileFromBuilder(builder ->
                        builder.from("postgres:17.5")
                                .run("apt update && apt install -y postgresql-17-wal2json postgresql-contrib")
                                .build());
        static final DockerImageName PG_IMAGE = DockerImageName.parse(BASE_IMAGE.get())
                .asCompatibleSubstituteFor(PostgreSQLContainer.IMAGE);
        @SuppressWarnings("resource")
        static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER = new PostgreSQLContainer<>(PG_IMAGE)
                .withCommand("-c", "wal_level=logical");
        @Order(0)
        @RegisterExtension
        static final PostgresqlEndToEndExtension POSTGRESQL_EXTENSION = new PostgresqlEndToEndExtension(POSTGRESQL_CONTAINER);

        @Order(1)
        @RegisterExtension
        static final BeforeAllCallback SETUP = context -> {
            POSTGRESQL_EXTENSION.createDatabase(CONNECTOR.toLowerCase());
            NATS_EXTENSION.createStream("state_machine", "negotiations.>", "transfers.>");
            NATS_EXTENSION.createConsumer("state_machine", "cn-subscriber", "negotiations.>");
            NATS_EXTENSION.createConsumer("state_machine", "tp-subscriber", "transfers.>");
        };

        @Order(2)
        @RegisterExtension
        static final ControlPlaneExtension CONTROL_PLANE = ControlPlaneExtension.Builder.newInstance()
                .id("controlplane")
                .name("controlplane")
                .modules(":system-tests:runtimes:e2e:e2e-controlplane-postgres")
                .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(CONNECTOR.toLowerCase()))
                .configurationProvider(Postgres::runtimeConfiguration)
                .build();

        @Order(3)
        @RegisterExtension
        static final BeforeAllCallback SEED = context -> {
            POSTGRESQL_EXTENSION.execute(CONNECTOR.toLowerCase(), "ALTER TABLE edc_contract_negotiation REPLICA IDENTITY FULL;");
            POSTGRESQL_EXTENSION.execute(CONNECTOR.toLowerCase(), "ALTER TABLE edc_transfer_process REPLICA IDENTITY FULL;");
        };

        private static Config runtimeConfiguration() {
            return ConfigFactory.fromMap(new HashMap<>() {
                {
                    put("edc.postgres.cdc.url", POSTGRESQL_EXTENSION.getJdbcUrl(CONNECTOR.toLowerCase()));
                    put("edc.postgres.cdc.user", POSTGRESQL_EXTENSION.getUsername());
                    put("edc.postgres.cdc.password", POSTGRESQL_EXTENSION.getPassword());
                    put("edc.postgres.cdc.slot", "edc_cdc_slot_" + CONNECTOR.toLowerCase());
                    put("edc.nats.cn.subscriber.url", NATS_EXTENSION.getNatsUrl());
                    put("edc.nats.cn.publisher.url", NATS_EXTENSION.getNatsUrl());
                    put("edc.nats.tp.subscriber.url", NATS_EXTENSION.getNatsUrl());
                    put("edc.nats.tp.publisher.url", NATS_EXTENSION.getNatsUrl());
                }
            });
        }
    }

}
