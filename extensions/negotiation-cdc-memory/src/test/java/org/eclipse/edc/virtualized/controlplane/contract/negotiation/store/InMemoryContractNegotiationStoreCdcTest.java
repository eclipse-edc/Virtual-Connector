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

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.testfixtures.negotiation.store.ContractNegotiationStoreTestBase;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.ContractNegotiationChangeListener;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.eclipse.edc.connector.controlplane.contract.spi.testfixtures.negotiation.store.TestFunctions.createNegotiation;
import static org.eclipse.edc.connector.controlplane.contract.spi.testfixtures.negotiation.store.TestFunctions.createNegotiationBuilder;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.FINALIZED;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class InMemoryContractNegotiationStoreCdcTest extends ContractNegotiationStoreTestBase {

    private final InMemoryContractNegotiationStoreCdc store = new InMemoryContractNegotiationStoreCdc(CONNECTOR_NAME, clock, CriterionOperatorRegistryImpl.ofDefaults());

    @Override
    protected ContractNegotiationStore getContractNegotiationStore() {
        return store;
    }

    @Override
    protected void leaseEntity(String negotiationId, String owner, Duration duration) {
        store.acquireLease(negotiationId, owner, duration);
    }

    @Override
    protected boolean isLeasedBy(String negotiationId, String owner) {
        return store.isLeasedBy(negotiationId, owner);
    }


    @Test
    void dispatchOnCreate() {
        var cn = createNegotiation("test-negotiation-id");
        var listener = mock(ContractNegotiationChangeListener.class);
        store.registerChangeListener(listener);

        store.save(cn);

        verify(listener).onChange(isNull(), eq(cn));
    }

    @Test
    void dispatchOnUpdate() {
        var cnBuilder = createNegotiationBuilder("test-negotiation-id");
        var cn = cnBuilder.build().copy();

        var listener = mock(ContractNegotiationChangeListener.class);
        store.save(cn);
        store.registerChangeListener(listener);

        var updatedCn = cnBuilder.state(FINALIZED.code()).build();
        store.save(updatedCn);
        verify(listener).onChange(eq(cn), eq(updatedCn));
    }

    @Test
    void dispatchOnUpdate_shouldNotInvokeWhenStateDoesNotChange() {
        var cnBuilder = createNegotiationBuilder("test-negotiation-id");
        var cn = cnBuilder.build().copy();

        var listener = mock(ContractNegotiationChangeListener.class);
        store.save(cn);
        store.registerChangeListener(listener);

        var updatedCn = cnBuilder.build();
        store.save(updatedCn);

        verifyNoInteractions(listener);
    }
}
