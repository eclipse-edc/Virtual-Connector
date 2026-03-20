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
import org.eclipse.edc.connector.controlplane.transfer.spi.types.command.ResumeTransferCommand;
import org.eclipse.edc.spi.command.EntityCommandHandler;
import org.eclipse.edc.virtual.controlplane.tasks.Task;
import org.eclipse.edc.virtual.controlplane.tasks.TaskService;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.ResumeDataFlow;

import java.time.Clock;

import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.SUSPENDED;

/**
 * Resumes a SUSPENDED transfer process and puts it in the {@link TransferProcessStates#STARTING} state.
 */
public class ResumeTransferCommandHandler extends EntityCommandHandler<ResumeTransferCommand, TransferProcess> {

    private final TaskService taskService;
    private final Clock clock;

    public ResumeTransferCommandHandler(TransferProcessStore store, TaskService taskService, Clock clock) {
        super(store);
        this.taskService = taskService;
        this.clock = clock;
    }

    @Override
    public Class<ResumeTransferCommand> getType() {
        return ResumeTransferCommand.class;
    }

    @Override
    protected boolean modify(TransferProcess process, ResumeTransferCommand command) {
        if (process.currentStateIsOneOf(SUSPENDED)) {
            process.transitionResuming();
            return true;
        }

        return false;
    }

    @Override
    public void postActions(TransferProcess entity, ResumeTransferCommand command) {

        var payload = ResumeDataFlow.Builder.newInstance().processId(entity.getId())
                .processState(entity.stateAsString())
                .processType(entity.getType().name())
                .build();

        var task = Task.Builder.newInstance().at(clock.millis())
                .payload(payload)
                .build();


        taskService.create(task);
    }
}
