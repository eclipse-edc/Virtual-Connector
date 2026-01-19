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

import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessListener;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.virtual.controlplane.tasks.ProcessTaskPayload;
import org.eclipse.edc.virtual.controlplane.tasks.TaskService;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.CompleteDataFlow;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.PrepareTransfer;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.SignalDataflowStarted;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.StartDataflow;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.SuspendDataFlow;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.TerminateDataFlow;

import java.time.Clock;

public class TransferProcessStateListener extends StateListener implements TransferProcessListener {

    public TransferProcessStateListener(TaskService taskService, Clock clock) {
        super(taskService, clock);
    }

    @Override
    public void initiated(TransferProcess process) {
        var task = baseBuilder(PrepareTransfer.Builder.newInstance(), process)
                .build();
        storeTask(task);
    }

    @Override
    public void startupRequested(TransferProcess process) {
        var task = baseBuilder(SignalDataflowStarted.Builder.newInstance(), process)
                .build();

        storeTask(task);
    }

    @Override
    public void suspendingRequested(TransferProcess process) {
        var task = baseBuilder(SuspendDataFlow.Builder.newInstance(), process)
                .build();

        storeTask(task);
    }

    @Override
    public void startingRequested(TransferProcess process) {
        var task = baseBuilder(StartDataflow.Builder.newInstance(), process)
                .build();

        storeTask(task);
    }

    @Override
    public void terminatingRequested(TransferProcess process) {
        var task = baseBuilder(TerminateDataFlow.Builder.newInstance(), process)
                .build();

        storeTask(task);
    }

    @Override
    public void completingRequested(TransferProcess process) {
        var task = baseBuilder(CompleteDataFlow.Builder.newInstance(), process)
                .build();

        storeTask(task);
    }

    protected <T extends ProcessTaskPayload, B extends ProcessTaskPayload.Builder<T, B>> B baseBuilder(B builder, TransferProcess transferProcess) {
        return builder.processId(transferProcess.getId())
                .processState(transferProcess.stateAsString())
                .processType(transferProcess.getType().name());
    }
}
