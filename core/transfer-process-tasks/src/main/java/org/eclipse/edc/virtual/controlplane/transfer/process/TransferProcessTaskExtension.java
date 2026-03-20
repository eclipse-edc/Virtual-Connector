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

package org.eclipse.edc.virtual.controlplane.transfer.process;

import org.eclipse.edc.connector.controlplane.transfer.spi.TransferProcessPendingGuard;
import org.eclipse.edc.connector.controlplane.transfer.spi.TransferProcessors;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessObservable;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.command.CommandHandlerRegistry;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.edc.virtual.controlplane.tasks.TaskService;
import org.eclipse.edc.virtual.controlplane.transfer.process.listener.TransferProcessStateListener;
import org.eclipse.edc.virtual.controlplane.transfer.process.task.TransferProcessTaskExecutorImpl;
import org.eclipse.edc.virtual.controlplane.transfer.process.task.command.CompleteTransferCommandHandler;
import org.eclipse.edc.virtual.controlplane.transfer.process.task.command.ResumeTransferCommandHandler;
import org.eclipse.edc.virtual.controlplane.transfer.process.task.command.SuspendTransferCommandHandler;
import org.eclipse.edc.virtual.controlplane.transfer.process.task.command.TerminateTransferCommandHandler;
import org.eclipse.edc.virtual.controlplane.transfer.spi.TransferProcessTaskExecutor;

import java.time.Clock;

import static org.eclipse.edc.virtual.controlplane.transfer.process.TransferProcessTaskExtension.NAME;


@Extension(NAME)
public class TransferProcessTaskExtension implements ServiceExtension {

    public static final String NAME = "Transfer Task Extension";
    @Inject
    private TransferProcessStore store;

    @Inject
    private TransactionContext transactionContext;

    @Inject
    private TransferProcessPendingGuard pendingGuard;

    @Inject
    private Monitor monitor;

    @Inject
    private TaskService taskService;

    @Inject
    private TransferProcessors transferProcessors;

    @Inject
    private Clock clock;

    @Inject
    private CommandHandlerRegistry commandHandlerRegistry;

    @Inject
    private TransferProcessObservable transferProcessObservable;

    @Override
    public void initialize(ServiceExtensionContext context) {
        transferProcessObservable.registerListener(new TransferProcessStateListener(taskService, clock));
    }

    // temporary workaround to register command handlers for overriding the default ones
    @Override
    public void prepare() {
        commandHandlerRegistry.register(new SuspendTransferCommandHandler(store, taskService, clock));
        commandHandlerRegistry.register(new ResumeTransferCommandHandler(store, taskService, clock));
        commandHandlerRegistry.register(new TerminateTransferCommandHandler(store, taskService, clock));
        commandHandlerRegistry.register(new CompleteTransferCommandHandler(store, taskService, clock));
    }

    @Provider
    public TransferProcessTaskExecutor transferProcessTaskExecutor() {
        return TransferProcessTaskExecutorImpl.Builder.newInstance()
                .store(store)
                .transactionContext(transactionContext)
                .monitor(monitor)
                .pendingGuard(pendingGuard)
                .taskService(taskService)
                .transferProcessors(transferProcessors)
                .clock(clock)
                .build();
    }

}
