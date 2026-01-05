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

package org.eclipse.edc.virtual.controlplane.tasks.store;

import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.virtual.controlplane.tasks.Task;

import java.util.List;

public interface TaskStore {

    void create(Task task);

    List<Task> fetchForUpdate(QuerySpec querySpec);

    void delete(String id);

    void update(Task task);

    Task findById(String id);
}
