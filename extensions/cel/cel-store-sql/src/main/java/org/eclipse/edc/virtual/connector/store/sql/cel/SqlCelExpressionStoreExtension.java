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

package org.eclipse.edc.virtual.connector.store.sql.cel;


import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.bootstrapper.SqlSchemaBootstrapper;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.edc.virtual.connector.store.sql.cel.schema.postgres.PostgresDialectStatementsConfig;
import org.eclipse.edc.virtual.policy.cel.store.CelExpressionStore;

import static org.eclipse.edc.virtual.connector.store.sql.cel.SqlCelExpressionStoreExtension.NAME;


@Extension(value = NAME)
public class SqlCelExpressionStoreExtension implements ServiceExtension {
    public static final String NAME = "Cel Expressions SQL Store Extension";

    @Setting(description = "The datasource to be used", defaultValue = DataSourceRegistry.DEFAULT_DATASOURCE, key = "edc.sql.store.cel.datasource")
    private String dataSourceName;

    @Inject
    private DataSourceRegistry dataSourceRegistry;
    @Inject
    private TransactionContext transactionContext;
    @Inject
    private TypeManager typemanager;
    @Inject
    private QueryExecutor queryExecutor;
    @Inject(required = false)
    private CelExpressionStoreStatements statements;
    @Inject
    private SqlSchemaBootstrapper sqlSchemaBootstrapper;

    @Override
    public void initialize(ServiceExtensionContext context) {
        sqlSchemaBootstrapper.addStatementFromResource(dataSourceName, "cel-expression-schema.sql");
    }

    @Provider
    public CelExpressionStore createSqlStore() {
        return new SqlCelExpressionStore(dataSourceRegistry, dataSourceName, transactionContext, typemanager.getMapper(), queryExecutor, getStatementImpl());
    }

    private CelExpressionStoreStatements getStatementImpl() {
        return statements != null ? statements : new PostgresDialectStatementsConfig();
    }

}
