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

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.virtualized.controlplane.contract.negotiation.store.ContractNegotiationChangeRegistry;
import org.eclipse.edc.virtualized.controlplane.contract.negotiation.store.InMemoryContractNegotiationStoreCdc;

import java.time.Clock;

public class ContractNegotiationMemoryCdcExtension implements ServiceExtension {

    @Inject
    private Clock clock;

    @Inject
    private CriterionOperatorRegistry criterionOperatorRegistry;

    private InMemoryContractNegotiationStoreCdc contractNegotiationStore;

    private InMemoryContractNegotiationStoreCdc contractNegotiationStoreImpl() {
        if (contractNegotiationStore == null) {
            contractNegotiationStore = new InMemoryContractNegotiationStoreCdc(clock, criterionOperatorRegistry);
        }
        return contractNegotiationStore;
    }

    @Provider
    public ContractNegotiationStore contractNegotiationStore() {
        return contractNegotiationStoreImpl();
    }

    @Provider
    public ContractNegotiationChangeRegistry contractNegotiationChangeRegistry() {
        return contractNegotiationStoreImpl();
    }
}
