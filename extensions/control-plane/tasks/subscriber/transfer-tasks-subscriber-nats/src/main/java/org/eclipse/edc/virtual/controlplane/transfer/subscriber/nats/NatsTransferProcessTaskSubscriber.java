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

package org.eclipse.edc.virtual.controlplane.transfer.subscriber.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Message;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.virtual.controlplane.tasks.Task;
import org.eclipse.edc.virtual.controlplane.transfer.spi.TransferProcessTaskExecutor;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.TransferProcessTaskPayload;
import org.eclipse.edc.virtual.nats.subscriber.NatsSubscriber;

import java.util.Objects;
import java.util.function.Supplier;

public class NatsTransferProcessTaskSubscriber extends NatsSubscriber {

    private Supplier<ObjectMapper> mapperSupplier;
    private TransferProcessTaskExecutor taskExecutor;

    private NatsTransferProcessTaskSubscriber() {
    }

    // TODO check if the task was actually stored

    protected StatusResult<Void> handleMessage(Message message) {
        try {
            var task = mapperSupplier.get().readValue(message.getData(), Task.class);
            if (task.getPayload() instanceof TransferProcessTaskPayload payload) {
                return taskExecutor.handle(payload);
            } else {
                return StatusResult.failure(ResponseStatus.FATAL_ERROR, "Invalid task payload type");
            }
        } catch (Exception e) {
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, e.getMessage());
        }
    }

    public static class Builder extends NatsSubscriber.Builder<NatsTransferProcessTaskSubscriber, Builder> {

        protected Builder() {
            super(new NatsTransferProcessTaskSubscriber());
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder mapperSupplier(Supplier<ObjectMapper> mapperSupplier) {
            subscriber.mapperSupplier = mapperSupplier;
            return self();
        }

        public Builder taskExecutor(TransferProcessTaskExecutor taskManager) {
            subscriber.taskExecutor = taskManager;
            return self();
        }

        @Override
        public Builder self() {
            return this;
        }

        @Override
        public NatsTransferProcessTaskSubscriber build() {
            Objects.requireNonNull(subscriber.mapperSupplier, "mapperSupplier must be set");
            Objects.requireNonNull(subscriber.taskExecutor, "stateMachineService must be set");
            return super.build();
        }
    }
}
