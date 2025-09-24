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

package org.eclipse.edc.virtualized.controlplane.contract.negotiation.store;

import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.defaults.storage.contractnegotiation.InMemoryContractNegotiationStore;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.ContractNegotiationChangeListener;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

public class InMemoryContractNegotiationStoreCdc extends InMemoryContractNegotiationStore implements ContractNegotiationChangeRegistry {

    private final List<ContractNegotiationChangeListener> changeListeners = new ArrayList<>();

    public InMemoryContractNegotiationStoreCdc(String leaseHolder, Clock clock, CriterionOperatorRegistry criterionOperatorRegistry) {
        super(leaseHolder, clock, criterionOperatorRegistry);
    }

    public InMemoryContractNegotiationStoreCdc(Clock clock, CriterionOperatorRegistry criterionOperatorRegistry) {
        super(clock, criterionOperatorRegistry);
    }
    
    @Override
    public void save(ContractNegotiation entity) {
        var prev = findById(entity.getId());
        super.save(entity);
        if (prev == null || prev.getState() != entity.getState()) {
            for (var changeListener : changeListeners) {
                changeListener.onChange(prev, entity);

            }
        }
    }

    @Override
    public void registerChangeListener(ContractNegotiationChangeListener listener) {
        changeListeners.add(listener);
    }
}
