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
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessObservable;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessStartedData;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ResourceManifest;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferCompletionMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferProcessAck;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferRemoteMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferRequestMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferStartMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferSuspensionMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferTerminationMessage;
import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.TransferProcessStateMachineService;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess.Type.CONSUMER;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess.Type.PROVIDER;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.COMPLETING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.COMPLETING_REQUESTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.INITIAL;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.PROVISIONED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.PROVISIONING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.REQUESTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.REQUESTING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.STARTING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.SUSPENDING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.SUSPENDING_REQUESTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.TERMINATING;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.TERMINATING_REQUESTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.from;
import static org.eclipse.edc.spi.response.ResponseStatus.FATAL_ERROR;
import static org.eclipse.edc.spi.types.domain.DataAddress.EDC_DATA_ADDRESS_SECRET;

public class TransferProcessStateMachineServiceImpl implements TransferProcessStateMachineService {

    private final Map<TransferProcessStates, Handler> stateHandlers = new HashMap<>();

    private TransferProcessStore store;
    private TransactionContext transactionContext;
    private DataFlowManager dataFlowManager;
    private RemoteMessageDispatcherRegistry dispatcherRegistry;
    private DataspaceProfileContextRegistry dataspaceProfileContextRegistry;
    private Vault vault;
    private DataAddressResolver addressResolver;
    private TransferProcessObservable observable;
    private PolicyArchive policyArchive;
    private TransferProcessPendingGuard pendingGuard;
    private Monitor monitor;

    private TransferProcessStateMachineServiceImpl() {
        registerStateHandlers();
    }
    
    private void registerStateHandlers() {
        stateHandlers.put(INITIAL, new Handler(this::processInitial, null));
        stateHandlers.put(REQUESTING, new Handler(this::processRequesting, CONSUMER));
        stateHandlers.put(STARTING, new Handler(this::processStarting, PROVIDER));
        stateHandlers.put(PROVISIONING, new Handler(this::processProvisioning, PROVIDER));
        stateHandlers.put(PROVISIONED, new Handler(this::processProvisioned, null));
        stateHandlers.put(TERMINATING, new Handler(this::processTerminating, null));
        stateHandlers.put(TERMINATING_REQUESTED, new Handler(this::processTerminating, null));
        stateHandlers.put(COMPLETING, new Handler(this::processCompleting, null));
        stateHandlers.put(COMPLETING_REQUESTED, new Handler(this::processCompleting, null));
        stateHandlers.put(SUSPENDING, new Handler(this::processSuspending, null));
        stateHandlers.put(SUSPENDING_REQUESTED, new Handler(this::processSuspending, null));
    }

    @Override
    public StatusResult<Void> handle(String transferId, TransferProcessStates state) {
        return handleEvent(transferId, state);
    }

    private StatusResult<Void> handleEvent(String transferId, TransferProcessStates expectedState) {
        return transactionContext.execute(() -> {
            var transferResult = loadTransferProcess(transferId);
            if (transferResult.failed()) {
                return StatusResult.failure(FATAL_ERROR, transferResult.getFailureDetail());
            }

            var negotiation = transferResult.getContent();
            if (TransferProcessStates.isFinal(negotiation.getState())) {
                monitor.debug("Skipping transfer process with id '%s' is in final state '%s'".formatted(transferId, from(negotiation.getState())));
                return StatusResult.success();
            }

            if (negotiation.getState() != expectedState.code()) {
                monitor.warning("Skipping transfer process with id '%s' is in state '%s', expected '%s'".formatted(transferId, from(negotiation.getState()), expectedState));
                return StatusResult.success();
            }

            var handler = stateHandlers.get(expectedState);
            if (handler == null) {
                monitor.debug("No handler for state '%s' in transfer process with id '%s'".formatted(expectedState, transferId));
                return StatusResult.success();
            }

            if (handler.type != null && handler.type != negotiation.getType()) {
                var msg = "Expected type '%s' for state '%s', but got '%s' for transfer process %s".formatted(handler.type, expectedState, negotiation.getType(), transferId);
                monitor.severe(msg);
                return StatusResult.failure(FATAL_ERROR, msg);
            }

            if (pendingGuard.test(negotiation)) {
                monitor.debug("Skipping '%s' for transfer process with id '%s' due matched guard".formatted(expectedState, transferId));
                return StatusResult.success();
            }

            return handler.function.apply(negotiation);
        });

    }

    private StatusResult<Void> processProvisioning(TransferProcess process) {
        transitionToProvisioned(process);
        return StatusResult.success();
    }

    private StatusResult<Void> processProvisioned(TransferProcess process) {
        if (CONSUMER == process.getType()) {
            transitionToRequesting(process);
        } else {
            transitionToStarting(process);
        }
        return StatusResult.success();
    }

    private StatusResult<Void> processInitial(TransferProcess process) {
        var contractId = process.getContractId();
        var policy = policyArchive.findPolicyForContract(contractId);

        if (policy == null) {
            transitionToTerminated(process, "Policy not found for contract: " + contractId);
            return StatusResult.failure(FATAL_ERROR, "Policy not found for contract: " + contractId);
        }

        if (process.getType() == CONSUMER) {
            // TODO handle provisioning with DPS
            process.setDataPlaneId(null);
            process.transitionRequesting();
        } else {
            var assetId = process.getAssetId();
            var dataAddress = addressResolver.resolveForAsset(assetId);
            if (dataAddress == null) {
                transitionToTerminating(process, "Asset not found: " + assetId);
                return StatusResult.failure(FATAL_ERROR, "Asset not found: " + assetId);
            }
            process.setContentDataAddress(dataAddress);
            process.transitionProvisioning(ResourceManifest.Builder.newInstance().build());
        }

        update(process);
        return StatusResult.success();
    }

    private StatusResult<Void> processStarting(TransferProcess process) {
        var policy = policyArchive.findPolicyForContract(process.getContractId());

        var result = dataFlowManager.start(process, policy);

        if (result.failed()) {
            transitionToTerminated(process, result.getFailureDetail());
            return StatusResult.failure(FATAL_ERROR, result.getFailureDetail());
        }
        var dataFlowResponse = result.getContent();
        var messageBuilder = TransferStartMessage.Builder.newInstance().dataAddress(dataFlowResponse.getDataAddress());
        process.setDataPlaneId(dataFlowResponse.getDataPlaneId());
        return dispatch(messageBuilder, process, Object.class)
                .onSuccess(c -> transitionToStarted(process))
                .mapEmpty();
    }

    private StatusResult<Void> processTerminating(TransferProcess process) {
        if (process.getType() == CONSUMER && process.getState() < REQUESTED.code()) {
            transitionToTerminated(process);
            return StatusResult.success();
        }
        var result = dataFlowManager.terminate(process);

        if (result.failed()) {
            transitionToTerminated(process, "Failed to terminate transfer process: " + result.getFailureDetail());
            return StatusResult.failure(FATAL_ERROR, result.getFailureDetail());
        }

        if (process.terminationWasRequestedByCounterParty()) {
            transitionToTerminated(process);
            return StatusResult.success();
        }
        return dispatch(TransferTerminationMessage.Builder.newInstance().reason(process.getErrorDetail()), process, Object.class)
                .onSuccess(c -> transitionToTerminated(process))
                .mapEmpty();
    }

    private StatusResult<Void> processCompleting(TransferProcess process) {
        if (process.completionWasRequestedByCounterParty()) {
            var result = dataFlowManager.terminate(process);
            if (result.failed()) {
                transitionToTerminated(process, "Failed to terminate transfer process: " + result.getFailureDetail());
                return StatusResult.failure(FATAL_ERROR, result.getFailureDetail());
            }
            transitionToCompleted(process);
            return StatusResult.success();
        } else {
            return dispatch(TransferCompletionMessage.Builder.newInstance(), process, Object.class)
                    .onSuccess(c -> transitionToCompleted(process))
                    .mapEmpty();
        }
    }

    private StatusResult<Void> processSuspending(TransferProcess process) {
        if (process.getType() == PROVIDER) {
            var result = dataFlowManager.suspend(process);
            if (result.failed()) {
                transitionToTerminated(process, "Failed to suspend transfer process: " + result.getFailureDetail());
                return StatusResult.failure(FATAL_ERROR, result.getFailureDetail());
            }
        }
        if (process.suspensionWasRequestedByCounterParty()) {
            transitionToSuspended(process);
            return StatusResult.success();
        } else {
            return dispatch(TransferSuspensionMessage.Builder.newInstance(), process, Object.class)
                    .onSuccess(c -> transitionToSuspended(process))
                    .mapEmpty();
        }
    }

    private StatusResult<Void> processRequesting(TransferProcess process) {
        var originalDestination = process.getDataDestination();
        var callbackAddress = dataspaceProfileContextRegistry.getWebhook(process.getProtocol());

        if (callbackAddress != null) {
            var dataDestination = Optional.ofNullable(originalDestination)
                    .map(DataAddress::getKeyName)
                    .map(vault::resolveSecret)
                    .map(secret -> DataAddress.Builder.newInstance().properties(originalDestination.getProperties()).property(EDC_DATA_ADDRESS_SECRET, secret).build())
                    .orElse(originalDestination);

            var messageBuilder = TransferRequestMessage.Builder.newInstance()
                    .callbackAddress(callbackAddress.url())
                    .dataDestination(dataDestination)
                    .transferType(process.getTransferType())
                    .contractId(process.getContractId());

            return dispatch(messageBuilder, process, TransferProcessAck.class)
                    .onSuccess(ack -> transitionToRequested(process, ack))
                    .mapEmpty();

        } else {
            transitionToTerminated(process, "No callback address found for protocol: " + process.getProtocol());
            return StatusResult.failure(FATAL_ERROR, "No callback address found for protocol: " + process.getProtocol());
        }
    }

    private StatusResult<TransferProcess> loadTransferProcess(String transferProcessId) {
        var transferProcess = store.findById(transferProcessId);
        if (transferProcess == null) {
            return StatusResult.failure(FATAL_ERROR, "Transfer process with id '%s' not found".formatted(transferProcessId));
        }
        return StatusResult.success(transferProcess);
    }

    protected void update(TransferProcess entity) {
        store.save(entity);
        monitor.debug(() -> "[%s] %s %s is now in state %s"
                .formatted(this.getClass().getSimpleName(), entity.getClass().getSimpleName(),
                        entity.getId(), entity.stateAsString()));
    }

    private void transitionToTerminated(TransferProcess process, String message) {
        process.setErrorDetail(message);
        monitor.warning(message);
        transitionToTerminated(process);
    }

    private void transitionToRequesting(TransferProcess process) {
        process.transitionRequesting();
        observable.invokeForEach(l -> l.preRequesting(process));
        update(process);
    }

    private void transitionToTerminated(TransferProcess process) {
        process.transitionTerminated();
        observable.invokeForEach(l -> l.preTerminated(process));
        update(process);
        observable.invokeForEach(l -> l.terminated(process));
    }

    private void transitionToTerminating(TransferProcess process, String message, Throwable... errors) {
        monitor.warning(message, errors);
        process.transitionTerminating(message);
        update(process);
    }

    private void transitionToStarting(TransferProcess transferProcess) {
        transferProcess.transitionStarting();
        update(transferProcess);
    }

    private void transitionToStarted(TransferProcess transferProcess) {
        transferProcess.transitionStarted();
        observable.invokeForEach(l -> l.preStarted(transferProcess));
        update(transferProcess);
        observable.invokeForEach(l -> l.started(transferProcess, TransferProcessStartedData.Builder.newInstance().build()));
    }

    private void transitionToProvisioned(TransferProcess transferProcess) {
        transferProcess.transitionProvisioned();
        update(transferProcess);
    }


    private void transitionToCompleted(TransferProcess transferProcess) {
        transferProcess.transitionCompleted();
        observable.invokeForEach(l -> l.preCompleted(transferProcess));
        update(transferProcess);
        observable.invokeForEach(l -> l.completed(transferProcess));
    }

    private void transitionToSuspended(TransferProcess process) {
        process.transitionSuspended();
        update(process);
        observable.invokeForEach(l -> l.suspended(process));
    }

    private void transitionToRequested(TransferProcess transferProcess, TransferProcessAck ack) {
        transferProcess.transitionRequested();
        transferProcess.setCorrelationId(ack.getProviderPid());
        observable.invokeForEach(l -> l.preRequested(transferProcess));
        update(transferProcess);
        observable.invokeForEach(l -> l.requested(transferProcess));
    }

    private <T> StatusResult<T> dispatch(TransferRemoteMessage.Builder<?, ?> messageBuilder,
                                         TransferProcess process, Class<T> responseType) {

        var contractPolicy = policyArchive.findPolicyForContract(process.getContractId());

        messageBuilder.protocol(process.getProtocol())
                .counterPartyAddress(process.getCounterPartyAddress())
                .processId(Optional.ofNullable(process.getCorrelationId()).orElse(process.getId()))
                .policy(contractPolicy);

        if (process.lastSentProtocolMessage() != null) {
            messageBuilder.id(process.lastSentProtocolMessage());
        }

        if (process.getType() == PROVIDER) {
            messageBuilder.consumerPid(process.getCorrelationId())
                    .providerPid(process.getId())
                    .counterPartyId(contractPolicy.getAssignee());
        } else {
            messageBuilder.consumerPid(process.getId())
                    .providerPid(process.getCorrelationId())
                    .counterPartyId(contractPolicy.getAssigner());
        }

        var message = messageBuilder.build();

        process.lastSentProtocolMessage(message.getId());

        try {
            return dispatcherRegistry.dispatch(responseType, message).get();
        } catch (Exception e) {
            return StatusResult.failure(FATAL_ERROR, "Failed to dispatch message: %s".formatted(e.getMessage()));
        }
    }

    private record Handler(Function<TransferProcess, StatusResult<Void>> function, TransferProcess.Type type) {

    }

    public static class Builder {

        private final TransferProcessStateMachineServiceImpl service;

        private Builder() {
            service = new TransferProcessStateMachineServiceImpl();
        }

        public static Builder newInstance() {
            return new Builder();
        }


        public TransferProcessStateMachineService build() {
            Objects.requireNonNull(service.dataFlowManager, "dataFlowManager cannot be null");
            Objects.requireNonNull(service.dispatcherRegistry, "dispatcherRegistry cannot be null");
            Objects.requireNonNull(service.observable, "observable cannot be null");
            Objects.requireNonNull(service.policyArchive, "policyArchive cannot be null");
            Objects.requireNonNull(service.addressResolver, "addressResolver cannot be null");
            Objects.requireNonNull(service.store, "store");
            Objects.requireNonNull(service.monitor, "monitor");
            Objects.requireNonNull(service.transactionContext, "transactionContext cannot be null");
            return service;
        }

        public Builder dataFlowManager(DataFlowManager dataFlowManager) {
            service.dataFlowManager = dataFlowManager;
            return this;
        }

        public Builder dispatcherRegistry(RemoteMessageDispatcherRegistry registry) {
            service.dispatcherRegistry = registry;
            return this;
        }

        public Builder vault(Vault vault) {
            service.vault = vault;
            return this;
        }

        public Builder store(TransferProcessStore store) {
            service.store = store;
            return this;
        }

        public Builder transactionContext(TransactionContext transactionContext) {
            service.transactionContext = transactionContext;
            return this;
        }

        public Builder monitor(Monitor monitor) {
            service.monitor = monitor;
            return this;
        }

        public Builder observable(TransferProcessObservable observable) {
            service.observable = observable;
            return this;
        }

        public Builder policyArchive(PolicyArchive policyArchive) {
            service.policyArchive = policyArchive;
            return this;
        }

        public Builder addressResolver(DataAddressResolver addressResolver) {
            service.addressResolver = addressResolver;
            return this;
        }

        public Builder dataspaceProfileContextRegistry(DataspaceProfileContextRegistry dataspaceProfileContextRegistry) {
            service.dataspaceProfileContextRegistry = dataspaceProfileContextRegistry;
            return this;
        }

        public Builder pendingGuard(TransferProcessPendingGuard pendingGuard) {
            service.pendingGuard = pendingGuard;
            return this;
        }
    }
}
