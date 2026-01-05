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


import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;
import java.util.UUID;

public class Task {
    protected String id;

    protected long at;

    protected TaskPayload payload;

    public long getAt() {
        return at;
    }

    public String getId() {
        return id;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
    public TaskPayload getPayload() {
        return payload;
    }


    public Task.Builder toBuilder() {
        return Builder.newInstance()
                .id(id)
                .at(at)
                .payload(payload);
    }

    public static class Builder {

        protected final Task task;

        protected Builder() {
            task = new Task();
        }

        public static  Task.Builder newInstance() {
            return new Task.Builder();
        }

        public Task.Builder id(String id) {
            task.id = id;
            return this;
        }

        public Task.Builder at(long at) {
            task.at = at;
            return this;
        }

        public Task.Builder payload(TaskPayload payload) {
            task.payload = payload;
            return this;
        }

        public Task build() {
            if (task.id == null) {
                task.id = UUID.randomUUID().toString();
            }
            if (task.at == 0) {
                throw new IllegalStateException("Event 'at' field must be set");
            }
            Objects.requireNonNull(task.payload, "Task payload must be set");
            return task;
        }

    }
}
