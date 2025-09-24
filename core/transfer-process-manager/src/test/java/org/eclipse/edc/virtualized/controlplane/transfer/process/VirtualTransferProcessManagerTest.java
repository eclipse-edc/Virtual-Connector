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

import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyArchive;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessListener;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessObservable;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferRequest;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.callback.CallbackAddress;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VirtualTransferProcessManagerTest {


    private final TransferProcessStore store = mock();
    private final TransferProcessListener listener = mock();
    private final TransferProcessObservable observable = new MockTransferObservable(listener);
    private final PolicyArchive policyArchive = mock();
    private final VirtualTransferProcessManager transferProcessManager = new VirtualTransferProcessManager(store, observable, policyArchive, Clock.systemUTC(), mock());

    @Test
    void initiateConsumerRequest() {

        when(policyArchive.findPolicyForContract(any())).thenReturn(Policy.Builder.newInstance().target("assetId").build());
        when(store.findForCorrelationId("1")).thenReturn(null);
        var callback = CallbackAddress.Builder.newInstance().uri("local://test").events(Set.of("test")).build();

        var transferRequest = TransferRequest.Builder.newInstance()
                .id("1")
                .dataDestination(DataAddress.Builder.newInstance().type("test").build())
                .callbackAddresses(List.of(callback))
                .build();

        var captor = ArgumentCaptor.forClass(TransferProcess.class);

        var result = transferProcessManager.initiateConsumerRequest(transferRequest);

        assertThat(result).isSucceeded().isNotNull();
        verify(store).save(captor.capture());
        var transferProcess = captor.getValue();
        assertThat(transferProcess.getId()).isEqualTo("1");
        assertThat(transferProcess.getCorrelationId()).isNull();
        assertThat(transferProcess.getCallbackAddresses()).usingRecursiveFieldByFieldElementComparator().contains(callback);
        assertThat(transferProcess.getAssetId()).isEqualTo("assetId");
        verify(listener).initiated(any());
    }

    @Test
    void shouldFail_whenPolicyNotAvailable() {
        when(policyArchive.findPolicyForContract(any())).thenReturn(null);
        when(store.findForCorrelationId("1")).thenReturn(null);

        var transferRequest = TransferRequest.Builder.newInstance()
                .id("1")
                .contractId("contractId")
                .dataDestination(DataAddress.Builder.newInstance().type("test").build())
                .build();

        var result = transferProcessManager.initiateConsumerRequest(transferRequest);

        assertThat(result).isFailed();
    }

}
