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

import org.eclipse.edc.junit.annotations.NightlyTest;
import org.eclipse.edc.junit.extensions.EmbeddedRuntime;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimePerClassExtension;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndExtension;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.SelinuxContext;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.fail;
import static org.eclipse.edc.util.io.Ports.getFreePort;

@NightlyTest
@Testcontainers
public class PostgresVirtualCompatibilityDockerTest {


    protected static final String CONNECTOR = "CUT";

    static final ImageFromDockerfile BASE_IMAGE = new ImageFromDockerfile()
            .withDockerfileFromBuilder(builder ->
                    builder.from("postgres:17.5")
                            .run("apt update && apt install -y postgresql-17-wal2json postgresql-contrib")
                            .build());

    static final DockerImageName PG_IMAGE = DockerImageName.parse(BASE_IMAGE.get())
            .asCompatibleSubstituteFor(PostgreSQLContainer.IMAGE);
    static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER = new PostgreSQLContainer<>(PG_IMAGE)
            .withCommand("-c", "wal_level=logical");

    @Order(0)
    @RegisterExtension
    static final PostgresqlEndToEndExtension POSTGRESQL_EXTENSION = new PostgresqlEndToEndExtension(POSTGRESQL_CONTAINER);

    @RegisterExtension
    protected static RuntimeExtension runtime = new RuntimePerClassExtension(new EmbeddedRuntime(CONNECTOR,
            ":system-tests:runtimes:tck:tck-controlplane-postgres"
    ).configurationProvider(PostgresVirtualCompatibilityDockerTest::runtimeConfiguration)
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(CONNECTOR.toLowerCase())));

    @Order(1)
    @RegisterExtension
    static final BeforeAllCallback CREATE_DATABASES = context -> {
        POSTGRESQL_EXTENSION.createDatabase(CONNECTOR.toLowerCase());
    };
    private static final GenericContainer<?> TCK_CONTAINER = new TckContainer<>("eclipsedataspacetck/dsp-tck-runtime:1.0.0-RC4");

    private static Config runtimeConfiguration() {
        return ConfigFactory.fromMap(new HashMap<>() {
            {
                put("edc.participant.id", "CONNECTOR_UNDER_TEST");
                put("web.http.port", "8080");
                put("web.http.path", "/api");
                put("web.http.version.port", String.valueOf(getFreePort()));
                put("web.http.version.path", "/api/version");
                put("web.http.control.port", String.valueOf(getFreePort()));
                put("web.http.control.path", "/api/control");
                put("web.http.management.port", "8081");
                put("web.http.management.path", "/api/management");
                put("web.http.protocol.port", "8282"); // this must match the configured connector url in resources/docker.tck.properties
                put("web.http.protocol.path", "/api/dsp"); // this must match the configured connector url in resources/docker.tck.properties
                put("web.api.auth.key", "password");
                put("edc.dsp.callback.address", "http://host.docker.internal:8282/api/dsp"); // host.docker.internal is required by the container to communicate with the host
                put("edc.management.context.enabled", "true");
                put("edc.hostname", "host.docker.internal");
                put("edc.component.id", "DSP-compatibility-test");
                put("edc.transfer.proxy.token.signer.privatekey.alias", "private-key");
                put("edc.transfer.proxy.token.verifier.publickey.alias", "public-key");
                put("edc.postgres.cdc.url", POSTGRESQL_EXTENSION.getJdbcUrl(CONNECTOR.toLowerCase()));
                put("edc.postgres.cdc.user", POSTGRESQL_EXTENSION.getUsername());
                put("edc.postgres.cdc.password", POSTGRESQL_EXTENSION.getPassword());
                put("edc.postgres.cdc.slot", "edc_cdc_slot_" + CONNECTOR.toLowerCase());

            }
        });
    }

    private static String resourceConfig(String resource) {
        return Path.of(TestUtils.getResource(resource)).toString();
    }

    @Timeout(300)
    @Test
    void assertDspCompatibility() {
        POSTGRESQL_EXTENSION.execute(CONNECTOR.toLowerCase(), "ALTER TABLE edc_contract_negotiation REPLICA IDENTITY FULL;");

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

