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

package org.eclipse.edc.virtualized.controlplane.transfer.store;

import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.testfixtures.store.TransferProcessStoreTestBase;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.TransferProcessChangeListener;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.FINALIZED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.testfixtures.store.TestFunctions.createTransferProcessBuilder;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class InMemoryTransferProcessStoreCdcTest extends TransferProcessStoreTestBase {

    private final InMemoryTransferProcessStoreCdc store = new InMemoryTransferProcessStoreCdc(CONNECTOR_NAME, clock, CriterionOperatorRegistryImpl.ofDefaults());

    @Override
    protected TransferProcessStore getTransferProcessStore() {
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
        var cn = createTransferProcessBuilder("test-negotiation-id").build();
        var listener = mock(TransferProcessChangeListener.class);
        store.registerChangeListener(listener);

        store.save(cn);

        verify(listener).onChange(isNull(), eq(cn));
    }

    @Test
    void dispatchOnUpdate() {
        var cnBuilder = createTransferProcessBuilder("test-negotiation-id");
        var cn = cnBuilder.build().copy();

        var listener = mock(TransferProcessChangeListener.class);
        store.save(cn);
        store.registerChangeListener(listener);

        var updatedCn = cnBuilder.state(FINALIZED.code()).build();
        store.save(updatedCn);
        verify(listener).onChange(eq(cn), eq(updatedCn));
    }

    @Test
    void dispatchOnUpdate_shouldNotInvokeWhenStateDoesNotChange() {
        var cnBuilder = createTransferProcessBuilder("test-negotiation-id");
        var cn = cnBuilder.build().copy();

        var listener = mock(TransferProcessChangeListener.class);
        store.save(cn);
        store.registerChangeListener(listener);

        var updatedCn = cnBuilder.build();
        store.save(updatedCn);

        verifyNoInteractions(listener);
    }
}
