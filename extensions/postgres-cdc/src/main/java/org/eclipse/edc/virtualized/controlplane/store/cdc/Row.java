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

import java.util.Map;

public class Row {

    private final Map<String, Object> values;

    public Row(Map<String, Object> values) {
        this.values = values;
    }


    public boolean isEmpty() {
        return values.isEmpty();
    }

    public String getString(String columnName) {
        Object value = values.get(columnName);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    public Integer getInt(String columnName) {
        Object value = values.get(columnName);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        throw new IllegalArgumentException("Value for column " + columnName + " is not an Integer: " + value);
    }

    public Long getLong(String columnName) {
        Object value = values.get(columnName);
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        throw new IllegalArgumentException("Value for column " + columnName + " is not a Long: " + value);
    }

    public Boolean getBoolean(String columnName) {
        Object value = values.get(columnName);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        throw new IllegalArgumentException("Value for column " + columnName + " is not a Boolean: " + value);
    }
}
