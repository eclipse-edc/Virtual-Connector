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
import org.eclipse.edc.connector.controlplane.transfer.spi.types.command.CompleteTransferCommand;
import org.eclipse.edc.spi.command.EntityCommandHandler;
import org.eclipse.edc.virtual.controlplane.tasks.Task;
import org.eclipse.edc.virtual.controlplane.tasks.TaskService;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.CompleteDataFlow;

import java.time.Clock;

/**
 * Completes a transfer process and sends it to the {@link TransferProcessStates#COMPLETED} state.
 */
public class CompleteTransferCommandHandler extends EntityCommandHandler<CompleteTransferCommand, TransferProcess> {

    private final TaskService taskService;
    private final Clock clock;

    public CompleteTransferCommandHandler(TransferProcessStore store, TaskService taskService, Clock clock) {
        super(store);
        this.taskService = taskService;
        this.clock = clock;
    }

    @Override
    public Class<CompleteTransferCommand> getType() {
        return CompleteTransferCommand.class;
    }

    @Override
    protected boolean modify(TransferProcess process, CompleteTransferCommand command) {
        if (process.canBeCompleted()) {
            process.transitionCompleting();
            return true;
        }
        return false;
    }

    @Override
    public void postActions(TransferProcess entity, CompleteTransferCommand command) {

        var payload = CompleteDataFlow.Builder.newInstance().processId(entity.getId())
                .processState(entity.stateAsString())
                .processType(entity.getType().name())
                .build();

        var task = Task.Builder.newInstance().at(clock.millis())
                .payload(payload)
                .build();

        taskService.create(task);
    }

}
