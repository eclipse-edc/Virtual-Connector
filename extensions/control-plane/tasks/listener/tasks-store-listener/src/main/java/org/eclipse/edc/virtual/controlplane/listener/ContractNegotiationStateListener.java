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

package org.eclipse.edc.virtual.controlplane.listener;

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.observe.ContractNegotiationListener;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.AgreeNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.FinalizeNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.RequestNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.VerifyNegotiation;
import org.eclipse.edc.virtual.controlplane.tasks.ProcessTaskPayload;
import org.eclipse.edc.virtual.controlplane.tasks.TaskService;

import java.time.Clock;

public class ContractNegotiationStateListener extends StateListener implements ContractNegotiationListener {

    public ContractNegotiationStateListener(TaskService taskService, Clock clock) {
        super(taskService, clock);
    }

    @Override
    public void initiated(ContractNegotiation negotiation) {
        var task = baseBuilder(RequestNegotiation.Builder.newInstance(), negotiation)
                .build();
        storeTask(task);
    }

    @Override
    public void requested(ContractNegotiation negotiation) {
        if (negotiation.getType() == ContractNegotiation.Type.PROVIDER) {
            var task = baseBuilder(AgreeNegotiation.Builder.newInstance(), negotiation)
                    .build();
            storeTask(task);
        }
    }

    @Override
    public void agreed(ContractNegotiation negotiation) {
        if (negotiation.getType() == ContractNegotiation.Type.CONSUMER) {
            var task = baseBuilder(VerifyNegotiation.Builder.newInstance(), negotiation)
                    .build();
            storeTask(task);
        }
    }

    @Override
    public void verified(ContractNegotiation negotiation) {
        if (negotiation.getType() == ContractNegotiation.Type.PROVIDER) {
            var task = baseBuilder(FinalizeNegotiation.Builder.newInstance(), negotiation)
                    .build();
            storeTask(task);
        }
    }


    protected <T extends ProcessTaskPayload, B extends ProcessTaskPayload.Builder<T, B>> B baseBuilder(B builder, ContractNegotiation negotiation) {
        return builder.processId(negotiation.getId())
                .processState(negotiation.stateAsString())
                .processType(negotiation.getType().name());
    }

}
