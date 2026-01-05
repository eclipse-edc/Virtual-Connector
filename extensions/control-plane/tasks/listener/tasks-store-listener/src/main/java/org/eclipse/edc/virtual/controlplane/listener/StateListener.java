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

import org.eclipse.edc.virtual.controlplane.tasks.Task;
import org.eclipse.edc.virtual.controlplane.tasks.TaskPayload;
import org.eclipse.edc.virtual.controlplane.tasks.TaskService;

import java.time.Clock;

public abstract class StateListener {

    private final TaskService taskService;
    private final Clock clock;

    protected StateListener(TaskService taskService, Clock clock) {
        this.taskService = taskService;
        this.clock = clock;
    }

    protected void storeTask(TaskPayload payload) {
        var task = Task.Builder.newInstance()
                .at(clock.millis())
                .payload(payload)
                .build();
        taskService.create(task);
    }

}
