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
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.virtual.controlplane.transfer.spi.TransferProcessStateMachineService;
import org.eclipse.edc.virtual.nats.subscriber.NatsSubscriber;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class NatsTransferProcessSubscriber extends NatsSubscriber {

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

    private TransferProcessStates getState(Map<String, Object> payload) {
        var state = (String) payload.get("state");
        return TransferProcessStates.valueOf(state);
    }

    private String getTransferProcessId(Map<String, Object> payload) {
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
