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

package org.eclipse.edc.virtual.controlplane.transfer.subscriber.nats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Message;
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
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.virtual.controlplane.transfer.spi.TransferProcessStateMachineService;
import org.eclipse.edc.virtual.controlplane.transfer.spi.events.TransferProcessCompleting;
import org.eclipse.edc.virtual.controlplane.transfer.spi.events.TransferProcessCompletingRequested;
import org.eclipse.edc.virtual.controlplane.transfer.spi.events.TransferProcessDeprovisioning;
import org.eclipse.edc.virtual.controlplane.transfer.spi.events.TransferProcessProvisioning;
import org.eclipse.edc.virtual.controlplane.transfer.spi.events.TransferProcessRequesting;
import org.eclipse.edc.virtual.controlplane.transfer.spi.events.TransferProcessStarting;
import org.eclipse.edc.virtual.controlplane.transfer.spi.events.TransferProcessSuspending;
import org.eclipse.edc.virtual.controlplane.transfer.spi.events.TransferProcessSuspendingRequested;
import org.eclipse.edc.virtual.controlplane.transfer.spi.events.TransferProcessTerminating;
import org.eclipse.edc.virtual.controlplane.transfer.spi.events.TransferProcessTerminatingRequested;
import org.eclipse.edc.virtual.nats.subscriber.NatsSubscriber;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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

public class NatsTransferProcessSubscriber extends NatsSubscriber {

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
    private Supplier<ObjectMapper> mapperSupplier;
    private TransferProcessStateMachineService stateMachineService;

    private NatsTransferProcessSubscriber() {
    }

    protected StatusResult<Void> handleMessage(Message message) {
        try {
            var envelope = mapperSupplier.get().readValue(message.getData(), new TypeReference<Map<String, Object>>() {
            });
            var transferProcessId = getTransferProcessId(envelope);
            var state = getState(envelope);
            return stateMachineService.handle(transferProcessId, state);
        } catch (Exception e) {
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, e.getMessage());
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


    public static class Builder extends NatsSubscriber.Builder<NatsTransferProcessSubscriber, Builder> {

        protected Builder() {
            super(new NatsTransferProcessSubscriber());
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder mapperSupplier(Supplier<ObjectMapper> mapperSupplier) {
            subscriber.mapperSupplier = mapperSupplier;
            return self();
        }

        public Builder stateMachineService(TransferProcessStateMachineService stateMachineService) {
            subscriber.stateMachineService = stateMachineService;
            return self();
        }

        @Override
        public Builder self() {
            return this;
        }

        @Override
        public NatsTransferProcessSubscriber build() {
            Objects.requireNonNull(subscriber.mapperSupplier, "mapperSupplier must be set");
            Objects.requireNonNull(subscriber.stateMachineService, "stateMachineService must be set");
            return super.build();
        }
    }
}
