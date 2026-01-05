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

package org.eclipse.edc.virtual.controlplane.tasks.defaults;

import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.query.QueryResolver;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.store.ReflectionBasedQueryResolver;
import org.eclipse.edc.virtual.controlplane.tasks.Task;
import org.eclipse.edc.virtual.controlplane.tasks.store.TaskStore;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryTaskStore implements TaskStore {

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final QueryResolver<Task> queryResolver;

    public InMemoryTaskStore(CriterionOperatorRegistry criterionOperatorRegistry) {
        this.queryResolver = new ReflectionBasedQueryResolver<>(Task.class, criterionOperatorRegistry);
    }

    @Override
    public void create(Task task) {
        tasks.put(task.getId(), task);
    }

    @Override
    public List<Task> fetchForUpdate(QuerySpec querySpec) {
        return queryResolver.query(tasks.values().stream().map(t -> t), querySpec)
                .map(t -> (Task) t)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String id) {
        tasks.remove(id);
    }

    @Override
    public void update(Task task) {
        tasks.put(task.getId(), task);
    }

    @Override
    public Task findById(String id) {
        return tasks.get(id);
    }
}
