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

import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.virtual.controlplane.tasks.Task;
import org.eclipse.edc.virtual.controlplane.tasks.TaskService;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.CompleteDataFlow;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.PrepareTransfer;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.SignalDataflowStarted;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.StartDataflow;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.SuspendDataFlow;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.TerminateDataFlow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferProcessStateListenerTest {

    private final TaskService taskService = mock();
    private final Clock clock = Clock.systemUTC();
    private TransferProcessStateListener listener;

    @BeforeEach
    void setUp() {
        listener = new TransferProcessStateListener(taskService, clock);
    }

    @Test
    void initiated_shouldCreatePrepareTransferTask() {
        var process = createTransferProcess("transfer-123", TransferProcess.Type.CONSUMER, "INITIAL");

        listener.initiated(process);

        var taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).create(taskCaptor.capture());

        var task = taskCaptor.getValue();
        assertThat(task.getPayload()).isInstanceOf(PrepareTransfer.class);
        var payload = (PrepareTransfer) task.getPayload();
        assertThat(payload.getProcessId()).isEqualTo("transfer-123");
        assertThat(payload.getProcessState()).isEqualTo("INITIAL");
        assertThat(payload.getProcessType()).isEqualTo("CONSUMER");
    }

    @Test
    void startupRequested_shouldCreateSignalStartedDataflowTask() {
        var process = createTransferProcess("transfer-123", TransferProcess.Type.CONSUMER, "STARTUP_REQUESTED");

        listener.startupRequested(process);

        var taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).create(taskCaptor.capture());

        var task = taskCaptor.getValue();
        assertThat(task.getPayload()).isInstanceOf(SignalDataflowStarted.class);
        var payload = (SignalDataflowStarted) task.getPayload();
        assertThat(payload.getProcessId()).isEqualTo("transfer-123");
    }

    @Test
    void suspendingRequested_shouldCreateSuspendDataFlowTask() {
        var process = createTransferProcess("transfer-123", TransferProcess.Type.PROVIDER, "SUSPENDING_REQUESTED");

        listener.suspendingRequested(process);

        var taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).create(taskCaptor.capture());

        var task = taskCaptor.getValue();
        assertThat(task.getPayload()).isInstanceOf(SuspendDataFlow.class);
        var payload = (SuspendDataFlow) task.getPayload();
        assertThat(payload.getProcessId()).isEqualTo("transfer-123");
    }

    @Test
    void startingRequested_shouldCreateStartDataflowTask() {
        var process = createTransferProcess("transfer-123", TransferProcess.Type.PROVIDER, "STARTING_REQUESTED");

        listener.startingRequested(process);

        var taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).create(taskCaptor.capture());

        var task = taskCaptor.getValue();
        assertThat(task.getPayload()).isInstanceOf(StartDataflow.class);
        var payload = (StartDataflow) task.getPayload();
        assertThat(payload.getProcessId()).isEqualTo("transfer-123");
    }

    @Test
    void terminatingRequested_shouldCreateTerminateDataFlowTask() {
        var process = createTransferProcess("transfer-123", TransferProcess.Type.CONSUMER, "TERMINATING_REQUESTED");

        listener.terminatingRequested(process);

        var taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).create(taskCaptor.capture());

        var task = taskCaptor.getValue();
        assertThat(task.getPayload()).isInstanceOf(TerminateDataFlow.class);
        var payload = (TerminateDataFlow) task.getPayload();
        assertThat(payload.getProcessId()).isEqualTo("transfer-123");
    }

    @Test
    void completingRequested_shouldCreateCompleteDataFlowTask() {
        var process = createTransferProcess("transfer-123", TransferProcess.Type.PROVIDER, "COMPLETING_REQUESTED");

        listener.completingRequested(process);

        var taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).create(taskCaptor.capture());

        var task = taskCaptor.getValue();
        assertThat(task.getPayload()).isInstanceOf(CompleteDataFlow.class);
        var payload = (CompleteDataFlow) task.getPayload();
        assertThat(payload.getProcessId()).isEqualTo("transfer-123");
    }

    @Test
    void shouldPreserveTransferProcessState() {
        var process = createTransferProcess("transfer-123", TransferProcess.Type.CONSUMER, "CUSTOM_STATE");

        listener.initiated(process);

        var taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).create(taskCaptor.capture());

        var task = taskCaptor.getValue();
        var payload = (PrepareTransfer) task.getPayload();
        assertThat(payload.getProcessState()).isEqualTo("CUSTOM_STATE");
    }

    @Test
    void shouldPreserveTransferProcessType() {
        var process = createTransferProcess("transfer-123", TransferProcess.Type.PROVIDER, "INITIAL");

        listener.initiated(process);

        var taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).create(taskCaptor.capture());

        var task = taskCaptor.getValue();
        var payload = (PrepareTransfer) task.getPayload();
        assertThat(payload.getProcessType()).isEqualTo("PROVIDER");
    }

    private TransferProcess createTransferProcess(String id, TransferProcess.Type type, String state) {
        var process = mock(TransferProcess.class);
        when(process.getId()).thenReturn(id);
        when(process.getType()).thenReturn(type);
        when(process.stateAsString()).thenReturn(state);
        return process;
    }
}
