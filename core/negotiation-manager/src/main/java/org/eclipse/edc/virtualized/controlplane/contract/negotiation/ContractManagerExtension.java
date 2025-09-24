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

package org.eclipse.edc.virtualized.controlplane.contract.negotiation;

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.ConsumerContractNegotiationManager;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.ContractNegotiationPendingGuard;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.observe.ContractNegotiationObservable;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.ContractNegotiationStateMachineService;

import java.time.Clock;

import static org.eclipse.edc.virtualized.controlplane.contract.negotiation.ContractManagerExtension.NAME;

@Extension(NAME)
public class ContractManagerExtension implements ServiceExtension {

    public static final String NAME = "EDC-V Contract Manager";
    @Inject
    private ContractNegotiationStore contractNegotiationStore;

    @Inject
    private TransactionContext transactionContext;

    @Inject
    private ContractNegotiationPendingGuard pendingGuard;

    @Inject
    private Monitor monitor;

    @Inject
    private DataspaceProfileContextRegistry dataspaceProfileContextRegistry;

    @Inject
    private RemoteMessageDispatcherRegistry dispatcherRegistry;

    @Inject
    private ContractNegotiationObservable observable;

    @Inject
    private Clock clock;

    @Provider
    public ConsumerContractNegotiationManager consumerContractNegotiationManager() {
        return new ConsumerContractNegotiationVirtual(contractNegotiationStore, observable, monitor);
    }

    @Provider
    public ContractNegotiationStateMachineService contractNegotiationStateMachineService(ServiceExtensionContext context) {
        return new ContractNegotiationStateMachineServiceImpl(context.getParticipantId(), clock, dataspaceProfileContextRegistry, dispatcherRegistry, contractNegotiationStore, transactionContext, pendingGuard, observable, monitor);
    }
}
