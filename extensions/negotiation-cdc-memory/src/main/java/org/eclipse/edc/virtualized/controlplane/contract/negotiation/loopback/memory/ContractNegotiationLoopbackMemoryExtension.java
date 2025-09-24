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

package org.eclipse.edc.virtualized.controlplane.contract.negotiation.loopback.memory;

import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.virtualized.controlplane.contract.negotiation.store.ContractNegotiationChangeRegistry;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.ContractNegotiationStateMachineService;

public class ContractNegotiationLoopbackMemoryExtension implements ServiceExtension {

    @Inject
    private Monitor monitor;

    @Inject
    private ContractNegotiationStateMachineService stateMachineService;

    @Inject
    private ContractNegotiationChangeRegistry registry;

    private ContractNegotiationLoopbackMemory loopbackMemory;

    @Override
    public void initialize(ServiceExtensionContext context) {
        loopbackMemory = new ContractNegotiationLoopbackMemory(stateMachineService, monitor);
        registry.registerChangeListener(loopbackMemory);
    }

    @Override
    public void start() {
        if (loopbackMemory != null) {
            loopbackMemory.start();
        }
    }

    @Override
    public void shutdown() {
        if (loopbackMemory != null) {
            loopbackMemory.stop();
        }
    }

}
