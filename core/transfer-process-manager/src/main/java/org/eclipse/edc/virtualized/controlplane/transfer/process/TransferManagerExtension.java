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

package org.eclipse.edc.virtualized.controlplane.transfer.process;

import org.eclipse.edc.connector.controlplane.asset.spi.index.DataAddressResolver;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyArchive;
import org.eclipse.edc.connector.controlplane.transfer.spi.TransferProcessManager;
import org.eclipse.edc.connector.controlplane.transfer.spi.TransferProcessPendingGuard;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowManager;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessObservable;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.TransferProcessStateMachineService;

import java.time.Clock;

import static org.eclipse.edc.virtualized.controlplane.transfer.process.TransferManagerExtension.NAME;


@Extension(NAME)
public class TransferManagerExtension implements ServiceExtension {

    public static final String NAME = "EDC-V Transfer Manager";
    @Inject
    private TransferProcessStore store;

    @Inject
    private TransactionContext transactionContext;

    @Inject
    private TransferProcessPendingGuard pendingGuard;

    @Inject
    private Monitor monitor;

    @Inject
    private DataspaceProfileContextRegistry dataspaceProfileContextRegistry;

    @Inject
    private RemoteMessageDispatcherRegistry dispatcherRegistry;

    @Inject
    private TransferProcessObservable observable;

    @Inject
    private PolicyArchive policyArchive;

    @Inject
    private DataFlowManager dataFlowManager;

    @Inject
    private DataAddressResolver dataAddressResolver;

    @Inject
    private Vault vault;

    @Inject
    private Clock clock;

    @Provider
    public TransferProcessManager transferProcessManager() {
        return new VirtualTransferProcessManager(store, observable, policyArchive, clock, monitor);
    }

    @Provider
    public TransferProcessStateMachineService transferProcessStateMachineService(ServiceExtensionContext context) {
        return TransferProcessStateMachineServiceImpl.Builder.newInstance()
                .store(store)
                .transactionContext(transactionContext)
                .dataFlowManager(dataFlowManager)
                .dispatcherRegistry(dispatcherRegistry)
                .dataspaceProfileContextRegistry(dataspaceProfileContextRegistry)
                .vault(vault)
                .addressResolver(dataAddressResolver)
                .monitor(monitor)
                .pendingGuard(pendingGuard)
                .observable(observable)
                .policyArchive(policyArchive)
                .build();
    }
}
