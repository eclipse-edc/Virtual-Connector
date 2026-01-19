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

package org.eclipse.edc.virtual.controlplane.tasks.sql;

import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.sql.translation.PostgresqlOperatorTranslator;
import org.eclipse.edc.sql.translation.SqlQueryStatement;
import org.eclipse.edc.virtual.controlplane.tasks.sql.schema.postgres.TaskMapping;

import static java.lang.String.format;

public class BaseSqlDialectStatementsTask implements TaskStatements {

    @Override
    public String getInsertTemplate() {
        return executeStatement()
                .column(getIdColumn())
                .column(getNameColumn())
                .jsonColumn(getPayloadColumn())
                .column(getTimestampColumn())
                .insertInto(getTaskTable());
    }

    @Override
    public String getUpdateTemplate() {
        return executeStatement()
                .column(getNameColumn())
                .jsonColumn(getPayloadColumn())
                .column(getTimestampColumn())
                .update(getTaskTable(), getIdColumn());
    }

    @Override
    public String findByIdTemplate() {
        return format("SELECT * FROM %s WHERE %s = ?", getTaskTable(), getIdColumn());
    }

    @Override
    public String fetchUnpublishedTemplate() {
        return format("SELECT * FROM %s ORDER BY %s ASC LIMIT ?", getTaskTable(), getTimestampColumn());
    }

    @Override
    public SqlQueryStatement createQuery(QuerySpec querySpec) {
        var select = format("SELECT * FROM %s", getTaskTable());
        return new SqlQueryStatement(select, querySpec, new TaskMapping(this), new PostgresqlOperatorTranslator());
    }

    @Override
    public String getDeleteStatement() {
        return executeStatement().delete(getTaskTable(), getIdColumn());
    }
}
