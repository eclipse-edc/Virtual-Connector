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

package org.eclipse.edc.virtualized.tck.dsp;

import org.eclipse.edc.junit.annotations.PostgresqlIntegrationTest;
import org.eclipse.edc.junit.extensions.ComponentRuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.participantcontext.spi.config.model.ParticipantContextConfiguration;
import org.eclipse.edc.participantcontext.spi.config.store.ParticipantContextConfigStore;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndExtension;
import org.eclipse.edc.virtualized.nats.testfixtures.NatsEndToEndExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.SelinuxContext;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.fail;
import static org.eclipse.edc.virtualized.test.system.fixtures.DockerImages.createPgContainer;

@PostgresqlIntegrationTest
@Testcontainers
public class PostgresVirtualCompatibilityDockerTest {


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
    private static final GenericContainer<?> TCK_CONTAINER = new TckContainer<>("eclipsedataspacetck/dsp-tck-runtime:1.0.0-RC4");

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
                put("edc.postgres.cdc.url", POSTGRESQL_EXTENSION.getJdbcUrl(CONNECTOR.toLowerCase()));
                put("edc.postgres.cdc.user", POSTGRESQL_EXTENSION.getUsername());
                put("edc.postgres.cdc.password", POSTGRESQL_EXTENSION.getPassword());
                put("edc.postgres.cdc.slot", "edc_cdc_slot_" + CONNECTOR.toLowerCase());
                put("edc.iam.oauth2.jwks.url", "https://example.com/jwks");
                put("edc.iam.oauth2.issuer", "test-issuer");
            }
        });
    }

    private static String resourceConfig(String resource) {
        return Path.of(TestUtils.getResource(resource)).toString();
    }

    @Timeout(300)
    @Test
    void assertDspCompatibility() {

        // pipe the docker container's log to this console at the INFO level
        var monitor = new ConsoleMonitor(">>> TCK Runtime (Docker)", ConsoleMonitor.Level.INFO, true);
        var reporter = new TckTestReporter();

        TCK_CONTAINER.addFileSystemBind(resourceConfig("docker.tck.properties"), "/etc/tck/config.properties", BindMode.READ_ONLY, SelinuxContext.SINGLE);
        TCK_CONTAINER.addFileSystemBind(resourceConfig("dspace-edc-context-v1.jsonld"), "/etc/tck/dspace-edc-context-v1.jsonld", BindMode.READ_ONLY, SelinuxContext.SINGLE);
        TCK_CONTAINER.withExtraHost("host.docker.internal", "host-gateway");
        TCK_CONTAINER.withLogConsumer(outputFrame -> monitor.info(outputFrame.getUtf8String()));
        TCK_CONTAINER.withLogConsumer(reporter);
        TCK_CONTAINER.waitingFor(new LogMessageWaitStrategy().withRegEx(".*Test run complete.*").withStartupTimeout(Duration.ofSeconds(300)));
        TCK_CONTAINER.start();

        var failures = reporter.failures();

        if (!failures.isEmpty()) {
            fail(failures.size() + " TCK test cases failed:\n" + String.join("\n", failures));
        }
    }


}

