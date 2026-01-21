/*
 *  Copyright (c) 2026 Metaform Systems, Inc.
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

import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.SelinuxContext;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.fail;

@Testcontainers
public abstract class DspCompatibilityDockerTestBase {

    public static final List<String> ALLOWED_FAILURES = List.of("TP:03-01", "TP:03-02", "TP:02-05", "TP:01-05");
    private static final GenericContainer<?> TCK_CONTAINER = new TckContainer<>("eclipsedataspacetck/dsp-tck-runtime:1.0.0-RC5");

    @Timeout(300)
    @Test
    void assertDspCompatibility() {

        // pipe the docker container's log to this console at the INFO level
        var monitor = new ConsoleMonitor(">>> TCK Runtime (Docker)", ConsoleMonitor.Level.INFO, true);
        var reporter = new TckTestReporter();

        TCK_CONTAINER.addFileSystemBind(dockerConfigFilePath(), "/etc/tck/config.properties", BindMode.READ_ONLY, SelinuxContext.SINGLE);
        TCK_CONTAINER.addFileSystemBind(resourceConfig("dspace-edc-context-v1.jsonld"), "/etc/tck/dspace-edc-context-v1.jsonld", BindMode.READ_ONLY, SelinuxContext.SINGLE);
        TCK_CONTAINER.withExtraHost("host.docker.internal", "host-gateway");
        TCK_CONTAINER.withLogConsumer(outputFrame -> monitor.info(outputFrame.getUtf8String()));
        TCK_CONTAINER.withLogConsumer(reporter);
        TCK_CONTAINER.waitingFor(new LogMessageWaitStrategy().withRegEx(".*Test run complete.*").withStartupTimeout(Duration.ofSeconds(300)));
        TCK_CONTAINER.start();

        var failures = reporter.failures();

        var allowedFailures = getAllowedFailures();
        failures.removeAll(allowedFailures);

        if (!failures.isEmpty()) {
            fail(failures.size() + " TCK test cases failed:\n" + String.join("\n", failures));
        }
    }

    protected String resourceConfig(String resource) {
        return Path.of(TestUtils.getResource(resource)).toString();
    }

    protected List<String> getAllowedFailures() {
        return ALLOWED_FAILURES;
    }

    protected String dockerConfigFilePath() {
        return resourceConfig("docker.tck.properties");
    }
}
