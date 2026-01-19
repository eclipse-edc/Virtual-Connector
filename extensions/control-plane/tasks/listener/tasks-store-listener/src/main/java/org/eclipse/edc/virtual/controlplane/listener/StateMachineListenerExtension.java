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

package org.eclipse.edc.virtual.controlplane.listener;

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.observe.ContractNegotiationObservable;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessObservable;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.edc.virtual.controlplane.tasks.TaskService;

import java.time.Clock;

public class StateMachineListenerExtension implements ServiceExtension {

    @Inject
    private TypeManager typeManager;

    @Inject
    private Clock clock;

    @Inject
    private EventRouter eventRouter;

    @Inject
    private TaskService taskService;

    @Inject
    private TransactionContext transactionContext;

    @Inject
    private TransferProcessObservable transferProcessObservable;

    @Inject
    private ContractNegotiationObservable contractNegotiationObservable;

    @Override
    public void initialize(ServiceExtensionContext context) {
        transferProcessObservable.registerListener(new TransferProcessStateListener(taskService, clock));
        contractNegotiationObservable.registerListener(new ContractNegotiationStateListener(taskService, clock));
    }
}
