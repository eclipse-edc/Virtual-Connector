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
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.ContractNegotiationTaskExecutor;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.ContractNegotiationTaskPayload;
import org.eclipse.edc.virtual.controlplane.tasks.subscriber.AbstractTaskSubscriber;

import java.util.Objects;
import java.util.function.Supplier;

public class NatsContractNegotiationTaskSubscriber extends AbstractTaskSubscriber<ContractNegotiationTaskPayload> {

    protected ContractNegotiationTaskExecutor taskExecutor;
    protected Supplier<ObjectMapper> mapperSupplier;

    private NatsContractNegotiationTaskSubscriber() {
        super(ContractNegotiationTaskPayload.class);
    }

    @Override
    protected StatusResult<Void> handlePayload(ContractNegotiationTaskPayload payload) {
        return taskExecutor.handle(payload);
    }

    public static class Builder extends AbstractTaskSubscriber.Builder<NatsContractNegotiationTaskSubscriber, Builder, ContractNegotiationTaskPayload> {

        protected Builder() {
            super(new NatsContractNegotiationTaskSubscriber());
        }

        public static Builder newInstance() {
            return new Builder();
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
            Objects.requireNonNull(subscriber.taskExecutor, "stateMachineService must be set");
            return super.build();
        }
    }
}
