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

package org.eclipse.edc.virtual.controlplane.tasks;

import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.edc.virtual.controlplane.tasks.store.TaskStore;

import java.util.List;

public class TaskServiceImpl implements TaskService {

    private final TaskStore taskStore;
    private final TaskObservable taskObservable;
    private final TransactionContext transactionContext;


    public TaskServiceImpl(TaskStore taskStore, TaskObservable taskObservable, TransactionContext transactionContext) {
        this.taskStore = taskStore;
        this.taskObservable = taskObservable;
        this.transactionContext = transactionContext;
    }

    @Override
    public void create(Task task) {
        transactionContext.execute(() -> {
            taskStore.create(task);
            taskObservable.invokeForEach(l -> l.created(task));
        });
    }

    @Override
    public List<Task> fetchLatestTask(QuerySpec query) {
        return transactionContext.execute(() -> taskStore.fetchForUpdate(query));
    }

    @Override
    public void delete(String id) {
        transactionContext.execute(() -> taskStore.delete(id));
    }

    @Override
    public void update(Task task) {
        transactionContext.execute(() -> taskStore.update(task));
    }

    @Override
    public Task findById(String id) {
        return transactionContext.execute(() -> taskStore.findById(id));
    }
}
