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

package org.eclipse.edc.virtual.tck.dsp;

import org.eclipse.edc.junit.annotations.PostgresqlIntegrationTest;
import org.eclipse.edc.junit.extensions.ComponentRuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.participantcontext.spi.config.model.ParticipantContextConfiguration;
import org.eclipse.edc.participantcontext.spi.config.store.ParticipantContextConfigStore;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndExtension;
import org.eclipse.edc.virtual.nats.testfixtures.NatsEndToEndExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.Map;

import static org.eclipse.edc.virtual.test.system.fixtures.DockerImages.createPgContainer;

@PostgresqlIntegrationTest
@Testcontainers
public class PostgresVirtualCompatibilityDockerTest extends DspCompatibilityDockerTestBase {


    protected static final String CONNECTOR = "CUT";

    @Order(0)
    @RegisterExtension
    static final PostgresqlEndToEndExtension POSTGRESQL_EXTENSION = new PostgresqlEndToEndExtension(createPgContainer());

    @Order(0)
    @RegisterExtension
    static final NatsEndToEndExtension NATS_EXTENSION = new NatsEndToEndExtension();
    @RegisterExtension
    static final RuntimeExtension RUNTIME = ComponentRuntimeExtension.Builder.newInstance()
            .name(CONNECTOR)
            .modules(":system-tests:runtimes:tck:tck-controlplane-postgres")
            .configurationProvider(PostgresVirtualCompatibilityDockerTest::runtimeConfiguration)
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(CONNECTOR.toLowerCase()))
            .configurationProvider(NATS_EXTENSION::configFor)
            .build();

    @Order(1)
    @RegisterExtension
    static final BeforeAllCallback SETUP = context -> {
        POSTGRESQL_EXTENSION.createDatabase(CONNECTOR.toLowerCase());
    };

    @BeforeAll
    static void setup(ParticipantContextConfigStore configStore) {
        var cfg = ParticipantContextConfiguration.Builder.newInstance()
                .participantContextId("participantContextId")
                .entries(Map.of("edc.participant.id", "participantContextId"))
                .build();
        configStore.save(cfg);

    }

    private static Config runtimeConfiguration() {
        return ConfigFactory.fromMap(new HashMap<>() {
            {
                put("edc.participant.id", "participantContextId");
                put("web.http.protocol.port", "8282"); // this must match the configured connector url in resources/docker.tck.properties
                put("web.http.protocol.path", "/api/dsp"); // this must match the configured connector url in resources/docker.tck.properties
                put("edc.dsp.callback.address", "http://host.docker.internal:8282/api/dsp"); // host.docker.internal is required by the container to communicate with the host
                put("edc.hostname", "host.docker.internal");
                put("edc.component.id", "DSP-compatibility-test");
                put("edc.transfer.proxy.token.signer.privatekey.alias", "private-key");
                put("edc.transfer.proxy.token.verifier.publickey.alias", "public-key");
                put("edc.iam.oauth2.jwks.url", "https://example.com/jwks");
                put("edc.iam.oauth2.issuer", "test-issuer");
            }
        });
    }
}

