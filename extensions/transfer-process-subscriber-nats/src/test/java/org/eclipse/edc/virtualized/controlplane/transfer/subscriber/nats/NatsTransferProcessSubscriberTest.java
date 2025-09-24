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

package org.eclipse.edc.virtualized.controlplane.transfer.subscriber.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.TransferProcessStateMachineService;
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
import org.eclipse.edc.virtualized.controlplane.transfer.subscriber.nats.NatsTransferProcessSubscriberExtension.NatsSubscriberConfig;
import org.eclipse.edc.virtualized.nats.testfixtures.NatsEndToEndExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.UUID;
import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;
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
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NatsTransferProcessSubscriberTest {

    public static final String STREAM_NAME = "stream_test";
    public static final String CONSUMER_NAME = "consumer_test";
    @Order(0)
    @RegisterExtension
    static final NatsEndToEndExtension NATS_EXTENSION = new NatsEndToEndExtension();
    private final TransferProcessStateMachineService stateMachineService = mock();
    private NatsTransferProcessSubscriber subscriber;

    @BeforeEach
    void beforeEach() {
        NATS_EXTENSION.createStream(STREAM_NAME, "transfers.>");
        NATS_EXTENSION.createConsumer(STREAM_NAME, CONSUMER_NAME, "transfers.>");
        var config = new NatsSubscriberConfig(NATS_EXTENSION.getNatsUrl(), CONSUMER_NAME, "transfers.>");
        subscriber = new NatsTransferProcessSubscriber(config, stateMachineService, ObjectMapper::new, mock());

    }

    @AfterEach
    void afterEach() {
        subscriber.stop();
        NATS_EXTENSION.deleteStream(STREAM_NAME);
    }

    @ParameterizedTest
    @ArgumentsSource(StateProvider.class)
    void handleMessage(TransferProcessStates expectedState, String eventType) {
        when(stateMachineService.handle(any(), any())).thenReturn(StatusResult.success());
        subscriber.start();
        var id = UUID.randomUUID().toString();
        var payload = """
                {
                    "type": "%s",
                    "payload": {
                        "transferProcessId": "%s"
                    }
                }
                """.formatted(eventType, id);

        NATS_EXTENSION.publish("transfers." + eventType.toLowerCase(), payload.getBytes());

        await().untilAsserted(() -> {
            verify(stateMachineService).handle(id, expectedState);
        });
    }


    public static class StateProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {

            return Stream.of(
                    arguments(INITIAL, TransferProcessInitiated.class.getSimpleName()),
                    arguments(REQUESTING, TransferProcessRequesting.class.getSimpleName()),
                    arguments(REQUESTED, TransferProcessRequested.class.getSimpleName()),
                    arguments(PROVISIONING, TransferProcessProvisioning.class.getSimpleName()),
                    arguments(PROVISIONING_REQUESTED, TransferProcessProvisioningRequested.class.getSimpleName()),
                    arguments(PROVISIONED, TransferProcessProvisioned.class.getSimpleName()),
                    arguments(STARTING, TransferProcessStarting.class.getSimpleName()),
                    arguments(STARTED, TransferProcessStarted.class.getSimpleName()),
                    arguments(SUSPENDING, TransferProcessSuspending.class.getSimpleName()),
                    arguments(SUSPENDING_REQUESTED, TransferProcessSuspendingRequested.class.getSimpleName()),
                    arguments(SUSPENDED, TransferProcessSuspended.class.getSimpleName()),
                    arguments(COMPLETING, TransferProcessCompleting.class.getSimpleName()),
                    arguments(COMPLETING_REQUESTED, TransferProcessCompletingRequested.class.getSimpleName()),
                    arguments(COMPLETED, TransferProcessCompleted.class.getSimpleName()),
                    arguments(TERMINATING, TransferProcessTerminating.class.getSimpleName()),
                    arguments(TERMINATING_REQUESTED, TransferProcessTerminatingRequested.class.getSimpleName()),
                    arguments(TERMINATED, TransferProcessTerminated.class.getSimpleName()),
                    arguments(DEPROVISIONING, TransferProcessDeprovisioning.class.getSimpleName()),
                    arguments(DEPROVISIONING_REQUESTED, TransferProcessDeprovisioningRequested.class.getSimpleName()),
                    arguments(DEPROVISIONED, TransferProcessDeprovisioned.class.getSimpleName())
            );
        }
    }


}
