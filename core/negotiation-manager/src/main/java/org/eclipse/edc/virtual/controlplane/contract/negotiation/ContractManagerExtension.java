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

package org.eclipse.edc.virtual.controlplane.contract.negotiation;

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.ConsumerContractNegotiationManager;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.ContractNegotiationPendingGuard;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.observe.ContractNegotiationObservable;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.participantcontext.spi.identity.ParticipantIdentityResolver;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.edc.virtual.controlplane.contract.negotiation.task.ContractNegotiationTaskExecutorImpl;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.ContractNegotiationStateMachineService;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.ContractNegotiationTaskExecutor;
import org.eclipse.edc.virtual.controlplane.participantcontext.spi.ParticipantWebhookResolver;
import org.eclipse.edc.virtual.controlplane.tasks.TaskService;

import java.time.Clock;

import static org.eclipse.edc.virtual.controlplane.contract.negotiation.ContractManagerExtension.NAME;

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
    private ParticipantWebhookResolver webhookResolver;

    @Inject
    private ParticipantIdentityResolver identityResolver;

    @Inject
    private RemoteMessageDispatcherRegistry dispatcherRegistry;

    @Inject
    private ContractNegotiationObservable observable;

    @Inject
    private TaskService taskService;

    @Inject
    private Clock clock;

    @Provider
    public ConsumerContractNegotiationManager consumerContractNegotiationManager() {
        return new VirtualConsumerContractNegotiationManager(contractNegotiationStore, observable, monitor);
    }

    @Provider
    public ContractNegotiationStateMachineService contractNegotiationStateMachineService(ServiceExtensionContext context) {
        return new ContractNegotiationStateMachineServiceImpl(clock, identityResolver, webhookResolver, dispatcherRegistry, contractNegotiationStore, transactionContext, pendingGuard, observable, monitor);
    }

    @Provider
    public ContractNegotiationTaskExecutor contractNegotiationTaskExecutor() {
        return ContractNegotiationTaskExecutorImpl.Builder.newInstance()
                .taskService(taskService)
                .clock(clock)
                .identityResolver(identityResolver)
                .webhookResolver(webhookResolver)
                .dispatcherRegistry(dispatcherRegistry)
                .store(contractNegotiationStore)
                .transactionContext(transactionContext)
                .pendingGuard(pendingGuard)
                .observable(observable)
                .monitor(monitor)
                .build();
    }
}
