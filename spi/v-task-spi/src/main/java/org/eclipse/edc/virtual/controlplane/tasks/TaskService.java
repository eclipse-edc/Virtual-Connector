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

import java.util.List;

public interface TaskService {

    void create(Task task);

    List<Task> fetchLatestTask(QuerySpec querySpec);

    void delete(String id);

    void update(Task task);

    Task findById(String id);
}
