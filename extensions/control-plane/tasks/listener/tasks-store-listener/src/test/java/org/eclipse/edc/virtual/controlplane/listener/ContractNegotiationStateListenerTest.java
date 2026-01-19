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

import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.AgreeNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.FinalizeNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.RequestNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.VerifyNegotiation;
import org.eclipse.edc.virtual.controlplane.tasks.Task;
import org.eclipse.edc.virtual.controlplane.tasks.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContractNegotiationStateListenerTest {

    private final TaskService taskService = mock();
    private final Clock clock = Clock.systemUTC();
    private ContractNegotiationStateListener listener;

    @BeforeEach
    void setUp() {
        listener = new ContractNegotiationStateListener(taskService, clock);
    }

    @Test
    void initiated_shouldCreateRequestNegotiationTask() {
        var negotiation = createContractNegotiation("negotiation-123", ContractNegotiation.Type.CONSUMER, "INITIAL");

        listener.initiated(negotiation);

        var taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).create(taskCaptor.capture());

        var task = taskCaptor.getValue();
        assertThat(task.getPayload()).isInstanceOf(RequestNegotiation.class);
        var payload = (RequestNegotiation) task.getPayload();
        assertThat(payload.getProcessId()).isEqualTo("negotiation-123");
        assertThat(payload.getProcessState()).isEqualTo("INITIAL");
        assertThat(payload.getProcessType()).isEqualTo("CONSUMER");
    }

    @Test
    void requested_shouldCreateAgreeNegotiationTaskWhenProvider() {
        var negotiation = createContractNegotiation("negotiation-123", ContractNegotiation.Type.PROVIDER, "REQUESTED");

        listener.requested(negotiation);

        var taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).create(taskCaptor.capture());

        var task = taskCaptor.getValue();
        assertThat(task.getPayload()).isInstanceOf(AgreeNegotiation.class);
        var payload = (AgreeNegotiation) task.getPayload();
        assertThat(payload.getProcessId()).isEqualTo("negotiation-123");
        assertThat(payload.getProcessType()).isEqualTo("PROVIDER");
    }

    @Test
    void requested_shouldNotCreateTaskWhenConsumer() {
        var negotiation = createContractNegotiation("negotiation-123", ContractNegotiation.Type.CONSUMER, "REQUESTED");

        listener.requested(negotiation);

        verifyNoInteractions(taskService);
    }

    @Test
    void agreed_shouldCreateVerifyNegotiationTaskWhenConsumer() {
        var negotiation = createContractNegotiation("negotiation-123", ContractNegotiation.Type.CONSUMER, "AGREED");

        listener.agreed(negotiation);

        var taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).create(taskCaptor.capture());

        var task = taskCaptor.getValue();
        assertThat(task.getPayload()).isInstanceOf(VerifyNegotiation.class);
        var payload = (VerifyNegotiation) task.getPayload();
        assertThat(payload.getProcessId()).isEqualTo("negotiation-123");
        assertThat(payload.getProcessType()).isEqualTo("CONSUMER");
    }

    @Test
    void agreed_shouldNotCreateTaskWhenProvider() {
        var negotiation = createContractNegotiation("negotiation-123", ContractNegotiation.Type.PROVIDER, "AGREED");

        listener.agreed(negotiation);

        verifyNoInteractions(taskService);
    }

    @Test
    void verified_shouldCreateFinalizeNegotiationTaskWhenProvider() {
        var negotiation = createContractNegotiation("negotiation-123", ContractNegotiation.Type.PROVIDER, "VERIFIED");

        listener.verified(negotiation);

        var taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).create(taskCaptor.capture());

        var task = taskCaptor.getValue();
        assertThat(task.getPayload()).isInstanceOf(FinalizeNegotiation.class);
        var payload = (FinalizeNegotiation) task.getPayload();
        assertThat(payload.getProcessId()).isEqualTo("negotiation-123");
        assertThat(payload.getProcessType()).isEqualTo("PROVIDER");
    }

    @Test
    void verified_shouldNotCreateTaskWhenConsumer() {
        var negotiation = createContractNegotiation("negotiation-123", ContractNegotiation.Type.CONSUMER, "VERIFIED");

        listener.verified(negotiation);

        verifyNoInteractions(taskService);
    }

    @Test
    void shouldPreserveNegotiationState() {
        var negotiation = createContractNegotiation("negotiation-123", ContractNegotiation.Type.CONSUMER, "CUSTOM_STATE");

        listener.initiated(negotiation);

        var taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).create(taskCaptor.capture());

        var task = taskCaptor.getValue();
        var payload = (RequestNegotiation) task.getPayload();
        assertThat(payload.getProcessState()).isEqualTo("CUSTOM_STATE");
    }

    private ContractNegotiation createContractNegotiation(String id, ContractNegotiation.Type type, String state) {
        var negotiation = mock(ContractNegotiation.class);
        when(negotiation.getId()).thenReturn(id);
        when(negotiation.getType()).thenReturn(type);
        when(negotiation.stateAsString()).thenReturn(state);
        return negotiation;
    }
}
