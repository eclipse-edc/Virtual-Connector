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

package org.eclipse.edc.virtualized.controlplane.transfer.spi.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessEvent;

/**
 * This event is raised when the TransferProcess has transition to provisioning requested.
 */
@JsonDeserialize(builder = TransferProcessSuspendingRequested.Builder.class)
public class TransferProcessSuspendingRequested extends TransferProcessEvent {

    private TransferProcessSuspendingRequested() {
    }

    @Override
    public String name() {
        return "transfer.process.suspendingRequested";
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder extends TransferProcessEvent.Builder<TransferProcessSuspendingRequested, Builder> {

        private Builder() {
            super(new TransferProcessSuspendingRequested());
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        @Override
        public Builder self() {
            return this;
        }
    }

}
