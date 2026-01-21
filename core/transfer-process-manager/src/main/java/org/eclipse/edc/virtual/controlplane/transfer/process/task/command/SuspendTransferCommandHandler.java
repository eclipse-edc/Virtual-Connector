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

package org.eclipse.edc.virtual.controlplane.transfer.process.task.command;

import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.command.SuspendTransferCommand;
import org.eclipse.edc.spi.command.EntityCommandHandler;
import org.eclipse.edc.virtual.controlplane.tasks.Task;
import org.eclipse.edc.virtual.controlplane.tasks.TaskService;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.SuspendDataFlow;

import java.time.Clock;

/**
 * Terminates a transfer process and puts it in the {@link TransferProcessStates#SUSPENDING} state.
 */
public class SuspendTransferCommandHandler extends EntityCommandHandler<SuspendTransferCommand, TransferProcess> {

    private final TaskService taskService;
    private final Clock clock;

    public SuspendTransferCommandHandler(TransferProcessStore store, TaskService taskService, Clock clock) {
        super(store);
        this.taskService = taskService;
        this.clock = clock;
    }

    @Override
    public Class<SuspendTransferCommand> getType() {
        return SuspendTransferCommand.class;
    }

    @Override
    protected boolean modify(TransferProcess process, SuspendTransferCommand command) {
        if (process.canBeSuspended()) {
            process.transitionSuspending(command.getReason());
            return true;
        }

        return false;
    }

    @Override
    public void postActions(TransferProcess entity, SuspendTransferCommand command) {

        var payload = SuspendDataFlow.Builder.newInstance().processId(entity.getId())
                .processState(entity.stateAsString())
                .processType(entity.getType().name())
                .build();

        var task = Task.Builder.newInstance().at(clock.millis())
                .payload(payload)
                .build();

        taskService.create(task);
    }
}
