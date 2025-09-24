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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DatabaseChangeTest {


    @Test
    void deserialize() throws JsonProcessingException {

        var payload = """
                {
                     "action": "U",
                     "schema": "public",
                     "table": "table",
                     "columns": [
                         {
                             "name": "a",
                             "type": "integer",
                             "value": 2
                         },
                         {
                             "name": "b",
                             "type": "character varying(30)",
                             "value": "Test2"
                         },
                         {
                             "name": "c",
                             "type": "timestamp without time zone",
                             "value": "2019-12-29 04:58:34.806671"
                         }
                     ],
                     "identity": [
                         {
                             "name": "a",
                             "type": "integer",
                             "value": 1
                         },
                         {
                             "name": "b",
                             "type": "character varying(30)",
                             "value": "Test"
                         },
                         {
                             "name": "c",
                             "type": "timestamp without time zone",
                             "value": "2019-12-29 04:58:34.806671"
                         }
                     ]
                 }
                """;
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        var change = mapper.readValue(payload, DatabaseChange.class);


        assertThat(change.action()).isEqualTo(DatabaseChange.Action.UPDATE);
        assertThat(change.table()).isEqualTo("table");
        assertThat(change.schema()).isEqualTo("public");
        assertThat(change.row()).satisfies(row -> {
            assertThat(row.getInt("a")).isEqualTo(2);
            assertThat(row.getString("b")).isEqualTo("Test2");
            assertThat(row.getString("c")).isEqualTo("2019-12-29 04:58:34.806671");
        });

        assertThat(change.oldRow()).satisfies(row -> {
            assertThat(row.getInt("a")).isEqualTo(1);
            assertThat(row.getString("b")).isEqualTo("Test");
            assertThat(row.getString("c")).isEqualTo("2019-12-29 04:58:34.806671");
        });
    }
}
