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

package org.eclipse.edc.virtualized.controlplane.contract.transfer.cdc.publisher.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Nats;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessCompleted;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessDeprovisioned;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessDeprovisioningRequested;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessEvent;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessInitiated;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessProvisioned;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessProvisioningRequested;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessRequested;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessStarted;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessSuspended;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessTerminated;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.virtualized.controlplane.contract.transfer.cdc.publisher.nats.TransferProcessCdcPublisherExtension.NatsPublisherConfig;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.TransferProcessChangeListener;
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

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.String.format;
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
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.from;
import static org.eclipse.edc.spi.response.ResponseStatus.FATAL_ERROR;

public class NatsTransferProcessChangePublisher implements TransferProcessChangeListener {

    public final AtomicBoolean active = new AtomicBoolean(false);
    private final NatsPublisherConfig config;
    private final Supplier<ObjectMapper> objectMapper;
    private final Clock clock;
    private final Map<TransferProcessStates, Function<TransferProcess, TransferProcessEvent>> eventTypeMap = new HashMap<>() {
        {
            put(INITIAL, builder(TransferProcessInitiated.Builder::newInstance));
            put(REQUESTING, builder(TransferProcessRequesting.Builder::newInstance));
            put(REQUESTED, builder(TransferProcessRequested.Builder::newInstance));
            put(PROVISIONING, builder(TransferProcessProvisioning.Builder::newInstance));
            put(PROVISIONING_REQUESTED, builder(TransferProcessProvisioningRequested.Builder::newInstance));
            put(PROVISIONED, builder(TransferProcessProvisioned.Builder::newInstance));
            put(STARTING, builder(TransferProcessStarting.Builder::newInstance));
            put(STARTED, builder(TransferProcessStarted.Builder::newInstance));
            put(SUSPENDING, builder(TransferProcessSuspending.Builder::newInstance));
            put(SUSPENDING_REQUESTED, builder(TransferProcessSuspendingRequested.Builder::newInstance));
            put(SUSPENDED, builder(TransferProcessSuspended.Builder::newInstance));
            put(COMPLETING, builder(TransferProcessCompleting.Builder::newInstance));
            put(COMPLETING_REQUESTED, builder(TransferProcessCompletingRequested.Builder::newInstance));
            put(COMPLETED, builder(TransferProcessCompleted.Builder::newInstance));
            put(TERMINATING, builder(TransferProcessTerminating.Builder::newInstance));
            put(TERMINATING_REQUESTED, builder(TransferProcessTerminatingRequested.Builder::newInstance));
            put(TERMINATED, builder(TransferProcessTerminated.Builder::newInstance));
            put(DEPROVISIONING, builder(TransferProcessDeprovisioning.Builder::newInstance));
            put(DEPROVISIONING_REQUESTED, builder(TransferProcessDeprovisioningRequested.Builder::newInstance));
            put(DEPROVISIONED, builder(TransferProcessDeprovisioned.Builder::newInstance));
        }
    };
    private JetStream js;
    private Connection connection;

    public NatsTransferProcessChangePublisher(NatsPublisherConfig config, Supplier<ObjectMapper> objectMapper, Clock clock) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @SuppressWarnings("unchecked")
    @Override
    public StatusResult<Void> onChange(TransferProcess before, TransferProcess after) {
        if (!active.get()) {
            return StatusResult.failure(FATAL_ERROR, "NATS Transfer process Change Listener is not active.");
        }
        var event = toEvent(after);
        if (event == null) {
            return StatusResult.success();
        }
        var payload = EventEnvelope.Builder.newInstance()
                .payload(event)
                .at(clock.millis())
                .build();
        try {
            var subject = format("%s.%s.%s", config.subjectPrefix(), after.getType().name().toLowerCase(), after.stateAsString().toLowerCase());
            var message = objectMapper.get().writeValueAsString(payload);
            js.publish(subject, message.getBytes());
            return StatusResult.success();
        } catch (Exception e) {
            return StatusResult.failure(FATAL_ERROR, "Failed to publish contract negotiation event: " + e.getMessage());
        }
    }

    public void start() {
        try {
            connection = Nats.connect(config.url());
            js = connection.jetStream();
            active.set(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public void stop() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                throw new RuntimeException("Failed to close NATS connection", e);
            }
        }
        active.set(false);
    }


    private TransferProcessEvent toEvent(TransferProcess cn) {
        var state = from(cn.getState());
        return Optional.ofNullable(eventTypeMap.get(state))
                .map(builder -> builder.apply(cn))
                .orElse(null);
    }

    private <T extends TransferProcessEvent, B extends TransferProcessEvent.Builder<T, B>> Function<TransferProcess, TransferProcessEvent> builder(Supplier<B> builder) {
        return negotiation -> {
            var b = baseBuilder(builder.get(), negotiation);
            return b.build();
        };
    }

    private <T extends TransferProcessEvent, B extends TransferProcessEvent.Builder<T, B>> B baseBuilder(B builder, TransferProcess negotiation) {
        return builder.transferProcessId(negotiation.getId())
                .contractId(negotiation.getContractId())
                .callbackAddresses(negotiation.getCallbackAddresses())
                .assetId(negotiation.getAssetId())
                .type(negotiation.getType().name());
    }


}
