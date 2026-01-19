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

package org.eclipse.edc.virtual.controlplane.contract.negotiation.subscriber;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Message;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.ContractNegotiationTaskExecutor;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.ContractNegotiationTaskPayload;
import org.eclipse.edc.virtual.controlplane.tasks.Task;
import org.eclipse.edc.virtual.nats.subscriber.NatsSubscriber;

import java.util.Objects;
import java.util.function.Supplier;

public class NatsContractNegotiationTaskSubscriber extends NatsSubscriber {

    protected ContractNegotiationTaskExecutor taskExecutor;
    protected Supplier<ObjectMapper> mapperSupplier;

    private NatsContractNegotiationTaskSubscriber() {
    }

    // TODO check if the task was actually stored
    @Override
    protected StatusResult<Void> handleMessage(Message message) {
        try {
            var task = mapperSupplier.get().readValue(message.getData(), Task.class);
            if (task.getPayload() instanceof ContractNegotiationTaskPayload payload) {
                return taskExecutor.handle(payload);
            } else {
                return StatusResult.failure(ResponseStatus.FATAL_ERROR, "Invalid task payload type");
            }
        } catch (Exception e) {
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, e.getMessage());
        }
    }

    public static class Builder extends NatsSubscriber.Builder<NatsContractNegotiationTaskSubscriber, Builder> {

        protected Builder() {
            super(new NatsContractNegotiationTaskSubscriber());
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder mapperSupplier(Supplier<ObjectMapper> mapperSupplier) {
            subscriber.mapperSupplier = mapperSupplier;
            return self();
        }

        public Builder taskExecutor(ContractNegotiationTaskExecutor taskExecutor) {
            subscriber.taskExecutor = taskExecutor;
            return self();
        }

        @Override
        public Builder self() {
            return this;
        }

        @Override
        public NatsContractNegotiationTaskSubscriber build() {
            Objects.requireNonNull(subscriber.mapperSupplier, "mapperSupplier must be set");
            Objects.requireNonNull(subscriber.taskExecutor, "stateMachineService must be set");
            return super.build();
        }
    }
}
