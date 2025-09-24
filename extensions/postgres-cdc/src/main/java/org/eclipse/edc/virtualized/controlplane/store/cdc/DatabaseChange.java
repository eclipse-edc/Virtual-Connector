/*
 *  Copyright (c) 2025 Metaform Systems, Inc.
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

package org.eclipse.edc.virtualized.controlplane.store.cdc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@JsonDeserialize(builder = DatabaseChange.Builder.class)
public class DatabaseChange {
    private String schema;
    private String table;
    private Row row;
    private Row oldRow;
    private Action action;

    private DatabaseChange() {

    }

    public Action action() {
        return action;
    }

    public String schema() {
        return schema;
    }

    public String table() {
        return table;
    }

    public Row row() {
        return row;
    }

    public Row oldRow() {
        return oldRow;
    }

    public enum Action {

        @JsonProperty("I")
        INSERT,
        @JsonProperty("U")
        UPDATE,
        @JsonProperty("D")
        DELETE,
        @JsonProperty("T")
        TRUNCATE
    }


    public record Column(@JsonProperty("name") String name, @JsonProperty("value") Object value) {
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private final DatabaseChange change;

        private Builder() {
            change = new DatabaseChange();
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        public Builder action(Action action) {
            change.action = action;
            return this;
        }

        public Builder schema(String schema) {
            change.schema = schema;
            return this;
        }

        public Builder table(String table) {
            change.table = table;
            return this;
        }

        public Builder columns(List<Column> columns) {
            change.row = toRow(columns);
            return this;
        }

        public Builder identity(List<Column> columns) {
            change.oldRow = toRow(columns);
            return this;
        }

        private Row toRow(List<Column> columns) {
            Map<String, Object> mutableColumns = new HashMap<>();
            if (columns != null) {
                for (Column column : columns) {
                    mutableColumns.put(column.name, column.value);
                }
            }
            return new Row(Collections.unmodifiableMap(mutableColumns));
        }

        public DatabaseChange build() {

            if (change.row == null) {
                change.row = new Row(Map.of());
            }
            if (change.oldRow == null) {
                change.oldRow = new Row(Map.of());
            }
            return change;
        }
    }
}
