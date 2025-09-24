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

package org.eclipse.edc.virtualized.controlplane.contract.negotiation.cdc.publisher.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.JetStreamApiException;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessCompleted;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessDeprovisioned;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessDeprovisioningRequested;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessInitiated;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessProvisioned;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessProvisioningRequested;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessRequested;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessStarted;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessSuspended;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessTerminated;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.virtualized.controlplane.contract.transfer.cdc.publisher.nats.NatsTransferProcessChangePublisher;
import org.eclipse.edc.virtualized.controlplane.contract.transfer.cdc.publisher.nats.TransferProcessCdcPublisherExtension.NatsPublisherConfig;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.events.TransferProcessCompleting;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.events.TransferProcessCompletingRequested;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.events.TransferProcessDeprovisioning;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.events.TransferProcessProvisioning;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.events.TransferProcessRequesting;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.events.TransferProcessStarting;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.events.TransferProcessSuspending;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.events.TransferProcessSuspendingRequested;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.events.TransferProcessTerminating;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.events.TransferProcessTerminatingRequested;
import org.eclipse.edc.virtualized.nats.testfixtures.NatsEndToEndExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Clock;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.COMPLETED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.COMPLETING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.COMPLETING_REQUESTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.DEPROVISIONED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.DEPROVISIONING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.DEPROVISIONING_REQUESTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.INITIAL;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.PROVISIONED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.PROVISIONING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.PROVISIONING_REQUESTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.REQUESTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.REQUESTING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.STARTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.STARTING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.SUSPENDED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.SUSPENDING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.SUSPENDING_REQUESTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.TERMINATED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.TERMINATING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.TERMINATING_REQUESTED;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;


@ComponentTest
@Testcontainers
public class NatsTransferProcessChangePublisherTest {

    public static final String STREAM_NAME = "stream_test";
    public static final String CONSUMER_NAME = "consumer_test";

    @Order(0)
    @RegisterExtension
    static final NatsEndToEndExtension NATS_EXTENSION = new NatsEndToEndExtension();

    private final ObjectMapper mapper = new ObjectMapper();
    private NatsTransferProcessChangePublisher publisher;


    @BeforeEach
    void beforeEach() throws JetStreamApiException, IOException {
        NATS_EXTENSION.createStream(STREAM_NAME, "transfers.>");
        NATS_EXTENSION.createConsumer(STREAM_NAME, CONSUMER_NAME);

        var config = new NatsPublisherConfig(NATS_EXTENSION.getNatsUrl(), "transfers");
        publisher = new NatsTransferProcessChangePublisher(config, () -> mapper, Clock.systemUTC()
        );
    }

    @AfterEach
    void afterEach() {
        NATS_EXTENSION.deleteStream(STREAM_NAME);
    }

    @ParameterizedTest
    @ArgumentsSource(TransferProcessProvider.class)
    void onChange(TransferProcess transferProcess, String expectedType) throws Exception {

        publisher.start();

        var result = publisher.onChange(null, transferProcess);

        assertThat(result).isSucceeded();

        var payload = NATS_EXTENSION.nextMessage(STREAM_NAME, CONSUMER_NAME);
        assertThat(payload.get("type")).isEqualTo(expectedType);
    }

    @Test
    void onChange_NotActive() {
        var result = publisher.onChange(null, null);
        assertThat(result).isFailed();

        var cn = TransferProcess.Builder.newInstance()
                .id("cn-1")
                .counterPartyAddress("http://example.com")
                .protocol("test-protocol")
                .build();

        publisher.start();
        result = publisher.onChange(null, cn);
        assertThat(result).isSucceeded();

        publisher.stop();
        result = publisher.onChange(null, cn);
        assertThat(result).isFailed();
    }

    public static class TransferProcessProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {

            var cnBuilder = TransferProcess.Builder.newInstance()
                    .id("cn-1")
                    .counterPartyAddress("http://example.com")
                    .protocol("test-protocol");

            return Stream.of(
                    arguments(cnBuilder.state(INITIAL.code()).build().copy(), TransferProcessInitiated.class.getSimpleName()),
                    arguments(cnBuilder.state(REQUESTING.code()).build().copy(), TransferProcessRequesting.class.getSimpleName()),
                    arguments(cnBuilder.state(REQUESTED.code()).build().copy(), TransferProcessRequested.class.getSimpleName()),
                    arguments(cnBuilder.state(PROVISIONING.code()).build().copy(), TransferProcessProvisioning.class.getSimpleName()),
                    arguments(cnBuilder.state(PROVISIONED.code()).build().copy(), TransferProcessProvisioned.class.getSimpleName()),
                    arguments(cnBuilder.state(PROVISIONING_REQUESTED.code()).build().copy(), TransferProcessProvisioningRequested.class.getSimpleName()),
                    arguments(cnBuilder.state(STARTING.code()).build().copy(), TransferProcessStarting.class.getSimpleName()),
                    arguments(cnBuilder.state(STARTED.code()).build().copy(), TransferProcessStarted.class.getSimpleName()),
                    arguments(cnBuilder.state(SUSPENDING.code()).build().copy(), TransferProcessSuspending.class.getSimpleName()),
                    arguments(cnBuilder.state(SUSPENDED.code()).build().copy(), TransferProcessSuspended.class.getSimpleName()),
                    arguments(cnBuilder.state(SUSPENDING_REQUESTED.code()).build().copy(), TransferProcessSuspendingRequested.class.getSimpleName()),
                    arguments(cnBuilder.state(COMPLETING.code()).build().copy(), TransferProcessCompleting.class.getSimpleName()),
                    arguments(cnBuilder.state(COMPLETED.code()).build().copy(), TransferProcessCompleted.class.getSimpleName()),
                    arguments(cnBuilder.state(COMPLETING_REQUESTED.code()).build().copy(), TransferProcessCompletingRequested.class.getSimpleName()),
                    arguments(cnBuilder.state(TERMINATING.code()).build().copy(), TransferProcessTerminating.class.getSimpleName()),
                    arguments(cnBuilder.state(TERMINATED.code()).build().copy(), TransferProcessTerminated.class.getSimpleName()),
                    arguments(cnBuilder.state(TERMINATING_REQUESTED.code()).build().copy(), TransferProcessTerminatingRequested.class.getSimpleName()),
                    arguments(cnBuilder.state(DEPROVISIONING.code()).build().copy(), TransferProcessDeprovisioning.class.getSimpleName()),
                    arguments(cnBuilder.state(DEPROVISIONED.code()).build().copy(), TransferProcessDeprovisioned.class.getSimpleName()),
                    arguments(cnBuilder.state(DEPROVISIONING_REQUESTED.code()).build().copy(), TransferProcessDeprovisioningRequested.class.getSimpleName())
            );
        }
    }
}
