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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Nats;
import io.nats.client.PullSubscribeOptions;
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
import org.eclipse.edc.spi.monitor.Monitor;
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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

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

public class NatsTransferProcessSubscriber {

    private final NatsSubscriberConfig config;
    private final TransferProcessStateMachineService stateMachineService;
    private final Supplier<ObjectMapper> mapperSupplier;
    private final Monitor monitor;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean active = new AtomicBoolean(false);

    private final Map<String, TransferProcessStates> stateMap = new HashMap<>() {
        {
            put(TransferProcessInitiated.class.getSimpleName(), INITIAL);
            put(TransferProcessRequested.class.getSimpleName(), REQUESTED);
            put(TransferProcessRequesting.class.getSimpleName(), REQUESTING);
            put(TransferProcessStarting.class.getSimpleName(), STARTING);
            put(TransferProcessStarted.class.getSimpleName(), STARTED);
            put(TransferProcessProvisioning.class.getSimpleName(), PROVISIONING);
            put(TransferProcessProvisioningRequested.class.getSimpleName(), PROVISIONING_REQUESTED);
            put(TransferProcessProvisioned.class.getSimpleName(), PROVISIONED);
            put(TransferProcessSuspending.class.getSimpleName(), SUSPENDING);
            put(TransferProcessSuspendingRequested.class.getSimpleName(), SUSPENDING_REQUESTED);
            put(TransferProcessSuspended.class.getSimpleName(), SUSPENDED);
            put(TransferProcessCompleting.class.getSimpleName(), COMPLETING);
            put(TransferProcessCompletingRequested.class.getSimpleName(), COMPLETING_REQUESTED);
            put(TransferProcessCompleted.class.getSimpleName(), COMPLETED);
            put(TransferProcessTerminating.class.getSimpleName(), TERMINATING);
            put(TransferProcessTerminatingRequested.class.getSimpleName(), TERMINATING_REQUESTED);
            put(TransferProcessTerminated.class.getSimpleName(), TERMINATED);
            put(TransferProcessDeprovisioning.class.getSimpleName(), DEPROVISIONING);
            put(TransferProcessDeprovisioningRequested.class.getSimpleName(), DEPROVISIONING_REQUESTED);
            put(TransferProcessDeprovisioned.class.getSimpleName(), DEPROVISIONED);
        }
    };

    private Connection connection;

    public NatsTransferProcessSubscriber(NatsSubscriberConfig config, TransferProcessStateMachineService stateMachineService, Supplier<ObjectMapper> mapperSupplier, Monitor monitor) {
        this.config = config;
        this.stateMachineService = stateMachineService;
        this.mapperSupplier = mapperSupplier;
        this.monitor = monitor;
    }

    public void start() {
        try {
            connection = Nats.connect(config.url());
            var js = connection.jetStream();
            var pullOptions = PullSubscribeOptions.builder()
                    .durable(config.name())
                    .build();

            var sub = js.subscribe(config.subject(), pullOptions);
            active.set(true);
            executorService.submit(() -> {
                run(sub);
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void run(JetStreamSubscription sub) {
        while (active.get()) {
            var messages = sub.fetch(100, Duration.ofMillis(100));
            for (var message : messages) {
                try {
                    var envelope = mapperSupplier.get().readValue(message.getData(), new TypeReference<Map<String, Object>>() {
                    });
                    var transferProcessId = getTransferProcessId(envelope);
                    var state = getState(envelope);
                    var result = stateMachineService.handle(transferProcessId, state);
                    if (result.failed()) {
                        monitor.severe("Failed to handle transfer process state change for ID: " + transferProcessId + ", state: " + state + ", reason: " + result.getFailureDetail());
                        message.nak();
                        continue;
                    }
                    message.ack();
                } catch (Exception e) {
                    monitor.severe("Failed to process transfer message: " + e.getMessage(), e);
                    message.nak();
                }
            }
        }
    }

    public void stop() {
        active.set(false);
        executorService.shutdown();
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private TransferProcessStates getState(Map<String, Object> envelope) {
        var state = (String) envelope.get("type");
        return stateMap.get(state);
    }

    @SuppressWarnings("unchecked")
    private String getTransferProcessId(Map<String, Object> envelope) {
        var payload = (Map<String, Object>) envelope.get("payload");
        return payload.get("transferProcessId").toString();
    }
}
