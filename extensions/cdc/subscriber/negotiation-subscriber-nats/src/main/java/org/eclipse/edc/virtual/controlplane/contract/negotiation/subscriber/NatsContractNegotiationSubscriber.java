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

package org.eclipse.edc.virtual.controlplane.contract.negotiation.subscriber;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Message;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.ContractNegotiationStateMachineService;
import org.eclipse.edc.virtual.nats.subscriber.NatsSubscriber;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class NatsContractNegotiationSubscriber extends NatsSubscriber {

    protected ContractNegotiationStateMachineService stateMachineService;
    protected Supplier<ObjectMapper> mapperSupplier;

    private NatsContractNegotiationSubscriber() {
    }

    @Override
    protected StatusResult<Void> handleMessage(Message message) {
        try {
            var envelope = mapperSupplier.get().readValue(message.getData(), new TypeReference<Map<String, Object>>() {
            });
            var contractNegotiationId = getContractNegotiationId(envelope);
            var state = getState(envelope);
            return stateMachineService.handle(contractNegotiationId, state);
        } catch (Exception e) {
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, e.getMessage());
        }
    }

    private ContractNegotiationStates getState(Map<String, Object> payload) {
        var state = payload.get("state").toString();
        return ContractNegotiationStates.valueOf(state);
    }

    private String getContractNegotiationId(Map<String, Object> payload) {
        return payload.get("contractNegotiationId").toString();
    }

    public static class Builder extends NatsSubscriber.Builder<NatsContractNegotiationSubscriber, Builder> {

        protected Builder() {
            super(new NatsContractNegotiationSubscriber());
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder mapperSupplier(Supplier<ObjectMapper> mapperSupplier) {
            subscriber.mapperSupplier = mapperSupplier;
            return self();
        }

        public Builder stateMachineService(ContractNegotiationStateMachineService stateMachineService) {
            subscriber.stateMachineService = stateMachineService;
            return self();
        }

        @Override
        public Builder self() {
            return this;
        }

        @Override
        public NatsContractNegotiationSubscriber build() {
            Objects.requireNonNull(subscriber.mapperSupplier, "mapperSupplier must be set");
            Objects.requireNonNull(subscriber.stateMachineService, "stateMachineService must be set");
            return super.build();
        }
    }
}
