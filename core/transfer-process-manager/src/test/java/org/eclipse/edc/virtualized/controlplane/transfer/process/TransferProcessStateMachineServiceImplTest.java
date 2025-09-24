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

import org.eclipse.edc.connector.controlplane.asset.spi.index.DataAddressResolver;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyArchive;
import org.eclipse.edc.connector.controlplane.transfer.spi.TransferProcessPendingGuard;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowManager;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessListener;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataFlowResponse;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferProcessAck;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.TransferProcessStateMachineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.ArgumentCaptor;

import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess.Type.CONSUMER;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess.Type.PROVIDER;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.COMPLETED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.COMPLETING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.COMPLETING_REQUESTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.INITIAL;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.PROVISIONED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.PROVISIONING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.REQUESTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.REQUESTING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.STARTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.STARTING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.SUSPENDED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.SUSPENDING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.SUSPENDING_REQUESTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.TERMINATED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.TERMINATING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.TERMINATING_REQUESTED;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class TransferProcessStateMachineServiceImplTest {


    private final RemoteMessageDispatcherRegistry dispatcherRegistry = mock();
    private final TransferProcessStore transferProcessStore = mock();
    private final PolicyArchive policyArchive = mock();
    private final DataFlowManager dataFlowManager = mock();
    private final Vault vault = mock();
    private final TransferProcessListener listener = mock();
    private final DataspaceProfileContextRegistry dataspaceProfileContextRegistry = mock();
    private final DataAddressResolver addressResolver = mock();
    private final String protocolWebhookUrl = "http://protocol.webhook/url";
    private final TransferProcessPendingGuard pendingGuard = mock();
    private final Monitor monitor = mock();
    private TransferProcessStateMachineService stateMachineService;

    @BeforeEach
    void setup() {
        stateMachineService = TransferProcessStateMachineServiceImpl.Builder.newInstance()
                .dataFlowManager(dataFlowManager)
                .dispatcherRegistry(dispatcherRegistry)
                .monitor(monitor)
                .observable(new MockTransferObservable(listener))
                .store(transferProcessStore)
                .policyArchive(policyArchive)
                .vault(vault)
                .addressResolver(addressResolver)
                .dataspaceProfileContextRegistry(dataspaceProfileContextRegistry)
                .pendingGuard(pendingGuard)
                .transactionContext(new NoopTransactionContext())
                .build();
    }


    @ParameterizedTest
    @ArgumentsSource(StateTransitionProvider.class)
    void handle(TransferProcessStates state, TransferProcess.Type type, TransferProcessStates expectedState) {

        var transferProcessId = "transferProcessId";
        var contractId = "contractId";

        when(dataFlowManager.start(any(), any())).thenReturn(StatusResult.success(DataFlowResponse.Builder.newInstance().build()));
        when(dataFlowManager.terminate(any())).thenReturn(StatusResult.success());
        when(dataFlowManager.suspend(any())).thenReturn(StatusResult.success());
        when(addressResolver.resolveForAsset(any())).thenReturn(DataAddress.Builder.newInstance().type("type").build());

        when(dispatcherRegistry.dispatch(any(), any())).thenReturn(completedFuture(StatusResult.success(TransferProcessAck.Builder.newInstance().build())));
        when(dataspaceProfileContextRegistry.getWebhook(any())).thenReturn(() -> protocolWebhookUrl);
        var transferProcess = TransferProcess.Builder.newInstance()
                .id(transferProcessId)
                .type(type)
                .state(state.code())
                .contractId(contractId)
                .build();

        when(transferProcessStore.findById(transferProcessId)).thenReturn(transferProcess);
        when(policyArchive.findPolicyForContract(any())).thenReturn(Policy.Builder.newInstance().build());


        var result = stateMachineService.handle(transferProcessId, state);

        assertThat(result).isSucceeded();

        var captor = ArgumentCaptor.forClass(TransferProcess.class);
        verify(transferProcessStore).save(captor.capture());

        var updatedProcess = captor.getValue();

        assertThat(updatedProcess.getState()).isEqualTo(expectedState.code());

    }

    @Test
    void handle_whenTransferNotFound() {
        var transferProcessId = "transferProcessId";

        when(transferProcessStore.findById(transferProcessId)).thenReturn(null);

        var result = stateMachineService.handle(transferProcessId, TERMINATING);
        assertThat(result).isFailed();


    }

    @Test
    void handle_whenInFinalState() {
        var transferProcessId = "transferProcessId";
        var transferProcess = TransferProcess.Builder.newInstance()
                .id(transferProcessId)
                .state(COMPLETED.code())
                .contractId("contractId")
                .build();

        when(transferProcessStore.findById(transferProcessId)).thenReturn(transferProcess);

        var result = stateMachineService.handle(transferProcessId, INITIAL);
        assertThat(result).isSucceeded();
        verify(transferProcessStore, times(0)).save(any());
        verify(monitor).debug(contains("is in final state"));
    }

    @Test
    void handle_whenNotExpectedState() {
        var transferProcessId = "transferProcessId";
        var transferProcess = TransferProcess.Builder.newInstance()
                .id(transferProcessId)
                .state(TERMINATING.code())
                .contractId("contractId")
                .build();

        when(transferProcessStore.findById(transferProcessId)).thenReturn(transferProcess);

        var result = stateMachineService.handle(transferProcessId, REQUESTING);
        assertThat(result).isSucceeded();
        verify(transferProcessStore, times(0)).save(any());
        verify(monitor).warning(contains("is in state 'TERMINATING', expected 'REQUESTING'"));
    }

    @Test
    void handle_whenNotHandler() {
        var transferProcessId = "transferProcessId";
        var transferProcess = TransferProcess.Builder.newInstance()
                .id(transferProcessId)
                .state(SUSPENDED.code())
                .contractId("contractId")
                .build();

        when(transferProcessStore.findById(transferProcessId)).thenReturn(transferProcess);

        var result = stateMachineService.handle(transferProcessId, SUSPENDED);
        assertThat(result).isSucceeded();
        verify(transferProcessStore, times(0)).save(any());
        verify(monitor).debug(startsWith("No handler for state"));
    }

    @Test
    void handle_whenWrongType() {
        var transferProcessId = "transferProcessId";
        var transferProcess = TransferProcess.Builder.newInstance()
                .id(transferProcessId)
                .state(REQUESTING.code())
                .type(PROVIDER)
                .contractId("contractId")
                .build();

        when(transferProcessStore.findById(transferProcessId)).thenReturn(transferProcess);

        var result = stateMachineService.handle(transferProcessId, REQUESTING);
        assertThat(result).isFailed().detail().contains("Expected type");
        verify(transferProcessStore, times(0)).save(any());
    }

    @Test
    void handle_skipOnPendingGuard() {
        var transferProcessId = "transferProcessId";
        var transferProcess = TransferProcess.Builder.newInstance()
                .id(transferProcessId)
                .state(REQUESTING.code())
                .type(CONSUMER)
                .contractId("contractId")
                .build();

        when(transferProcessStore.findById(transferProcessId)).thenReturn(transferProcess);
        when(pendingGuard.test(any())).thenReturn(true);

        var result = stateMachineService.handle(transferProcessId, REQUESTING);
        assertThat(result).isSucceeded();
        verify(transferProcessStore, times(0)).save(any());
        verify(monitor).debug(contains("due matched guard"));
    }


    public static class StateTransitionProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {


            return Stream.of(
                    arguments(INITIAL, CONSUMER, REQUESTING),
                    arguments(INITIAL, PROVIDER, PROVISIONING),
                    arguments(REQUESTING, CONSUMER, REQUESTED),
                    arguments(STARTING, PROVIDER, STARTED),
                    arguments(PROVISIONING, PROVIDER, PROVISIONED),
                    arguments(PROVISIONED, PROVIDER, STARTING),
                    arguments(PROVISIONED, CONSUMER, REQUESTING),
                    arguments(TERMINATING, CONSUMER, TERMINATED),
                    arguments(TERMINATING, PROVIDER, TERMINATED),
                    arguments(TERMINATING_REQUESTED, CONSUMER, TERMINATED),
                    arguments(TERMINATING_REQUESTED, PROVIDER, TERMINATED),
                    arguments(COMPLETING, CONSUMER, COMPLETED),
                    arguments(COMPLETING, PROVIDER, COMPLETED),
                    arguments(COMPLETING_REQUESTED, CONSUMER, COMPLETED),
                    arguments(COMPLETING_REQUESTED, PROVIDER, COMPLETED),
                    arguments(SUSPENDING, CONSUMER, SUSPENDED),
                    arguments(SUSPENDING, PROVIDER, SUSPENDED),
                    arguments(SUSPENDING_REQUESTED, CONSUMER, SUSPENDED),
                    arguments(SUSPENDING_REQUESTED, PROVIDER, SUSPENDED)
            );
        }
    }
}
