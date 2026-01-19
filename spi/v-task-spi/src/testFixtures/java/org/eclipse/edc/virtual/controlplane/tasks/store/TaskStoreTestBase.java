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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.virtual.controlplane.tasks.ProcessTaskPayload;
import org.eclipse.edc.virtual.controlplane.tasks.Task;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class TaskStoreTestBase {

    protected abstract TaskStore getStore();

    @Test
    void create_shouldStoreTask() {
        var payload = createTestPayload("process-1");
        var task = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload).build();

        getStore().create(task);

        var retrieved = getStore().findById(task.getId());
        assertThat(retrieved).usingRecursiveComparison().isEqualTo(task);
    }

    @Test
    void create_shouldGenerateUniqueIds() {
        var payload1 = createTestPayload("process-1");
        var task1 = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload1).build();

        var payload2 = createTestPayload("process-2");
        var task2 = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload2).build();

        getStore().create(task1);
        getStore().create(task2);

        assertThat(task1.getId()).isNotEqualTo(task2.getId());
        assertThat(getStore().findById(task1.getId())).usingRecursiveComparison().isEqualTo(task1);
        assertThat(getStore().findById(task2.getId())).usingRecursiveComparison().isEqualTo(task2);
    }

    @Test
    void findById_shouldReturnTaskWhenPresent() {
        var payload = createTestPayload("process-1");
        var task = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload).build();
        getStore().create(task);

        var retrieved = getStore().findById(task.getId());

        assertThat(retrieved).usingRecursiveComparison().isEqualTo(task);
    }

    @Test
    void findById_shouldReturnNullWhenNotFound() {
        var retrieved = getStore().findById("nonexistent-id");

        assertThat(retrieved).isNull();
    }

    @Test
    void delete_shouldRemoveTask() {
        var payload = createTestPayload("process-1");
        var task = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload).build();
        getStore().create(task);

        getStore().delete(task.getId());

        assertThat(getStore().findById(task.getId())).isNull();
    }

    @Test
    void delete_shouldNotThrowWhenTaskNotFound() {
        getStore().delete("nonexistent-id");
        // Should not throw
    }

    @Test
    void update_shouldModifyExistingTask() {
        var payload = createTestPayload("process-1");
        var task = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload).build();
        getStore().create(task);

        var updatedPayload = TestPayload.Builder.newInstance()
                .processId("process-2")
                .processState("UPDATED")
                .processType("PROVIDER")
                .build();

        var updatedTask = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .id(task.getId())
                .payload(updatedPayload)
                .build();
        getStore().update(updatedTask);

        var retrieved = getStore().findById(task.getId());
        assertThat(retrieved.getId()).isEqualTo(task.getId());
        assertThat(retrieved.getPayload()).usingRecursiveComparison().isEqualTo(updatedPayload);
    }

    @Test
    void fetchForUpdate() {
        var payload1 = createTestPayload("process-1");
        var task1 = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload1).build();

        var payload2 = createTestPayload("process-2");
        var task2 = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload2).build();

        getStore().create(task1);
        getStore().create(task2);

        var results = getStore().fetchForUpdate(QuerySpec.none());

        assertThat(results).hasSize(2)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrder(task1, task2);
    }

    @Test
    void fetchForUpdate_with_OrderBy() {
        var payload1 = createTestPayload("process-1");
        var task1 = Task.Builder.newInstance()
                .at(2000L)
                .payload(payload1).build();

        var payload2 = createTestPayload("process-2");
        var task2 = Task.Builder.newInstance()
                .at(1000L)
                .payload(payload2).build();

        getStore().create(task1);
        getStore().create(task2);

        var query = QuerySpec.Builder.newInstance().sortField("at")
                .build();

        var results = getStore().fetchForUpdate(query);

        assertThat(results).hasSize(2)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(task2, task1);
    }

    @Test
    void fetchForUpdate_empty() {
        var results = getStore().fetchForUpdate(QuerySpec.none());

        assertThat(results).isEmpty();
    }

    @Test
    void create_shouldAllowMultipleTasksForSameProcess() {
        var payload1 = createTestPayload("process-1");
        var task1 = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload1).build();

        var payload2 = createTestPayload("process-1");
        var task2 = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload2).build();

        getStore().create(task1);
        getStore().create(task2);

        var results = getStore().fetchForUpdate(QuerySpec.none());
        assertThat(results).hasSize(2);
    }

    private TestPayload createTestPayload(String processId) {
        return TestPayload.Builder.newInstance()
                .processId(processId)
                .processState("INITIAL")
                .processType("CONSUMER")
                .build();
    }


    public static class TestPayload extends ProcessTaskPayload {

        private TestPayload() {
        }

        @Override
        public String name() {
            return "transfer.prepare";
        }

        @JsonPOJOBuilder(withPrefix = "")
        public static class Builder extends ProcessTaskPayload.Builder<TestPayload, TestPayload.Builder> {

            private Builder() {
                super(new TestPayload());
            }

            @JsonCreator
            public static Builder newInstance() {
                return new TestPayload.Builder();
            }

            @Override
            public Builder self() {
                return this;
            }
        }
    }
}
