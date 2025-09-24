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

import org.eclipse.edc.runtime.metamodel.annotation.Configuration;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.Settings;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.ContractNegotiationChangeListener;
import org.eclipse.edc.virtualized.controlplane.store.cdc.listener.PostgresReplicationListener;
import org.eclipse.edc.virtualized.controlplane.store.cdc.listener.ReplicationConsumer;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.TransferProcessChangeListener;

import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

public class PostgresChangeDataCaptureExtension implements ServiceExtension {


    @Configuration
    private PostgresCdcConfig config;

    private PostgresReplicationListener replicationListener;

    @Inject
    private ContractNegotiationChangeListener contractNegotiationChangeListener;

    @Inject
    private TransferProcessChangeListener transferProcessChangeListener;

    @Inject
    private Monitor monitor;

    @Inject
    private TypeManager typeManager;

    @Override
    public void initialize(ServiceExtensionContext context) {
        var consumer = new ReplicationConsumer(contractNegotiationChangeListener, transferProcessChangeListener, () -> typeManager.getMapper(JSON_LD));
        replicationListener = new PostgresReplicationListener(config, consumer, () -> typeManager.getMapper(JSON_LD), monitor);
    }


    @Override
    public void start() {
        replicationListener.createReplicationSlot();
        replicationListener.start();
    }

    @Override
    public void shutdown() {
        replicationListener.stop();
    }


    @Settings
    public record PostgresCdcConfig(
            @Setting(key = "edc.postgres.cdc.url", description = "The JDBC URL for the PostgreSQL database")
            String jdbcUrl,
            @Setting(key = "edc.postgres.cdc.user", description = "The user for the PostgreSQL database")
            String username,
            @Setting(key = "edc.postgres.cdc.password", description = "The password for the PostgreSQL database")
            String password,
            @Setting(key = "edc.postgres.cdc.slot", description = "The name of the replication slot to use for change data capture", defaultValue = "edc_cdc_slot")
            String replicationSlotName,
            @Setting(key = "edc.postgres.cdc.tables", description = "The tables to listen for changes", defaultValue = "public.edc_contract_negotiation, public.edc_transfer_process")
            String tables
    ) {

    }
}
