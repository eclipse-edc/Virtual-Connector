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

package org.eclipse.edc.virtual.controlplane.transfer.process.task;

import org.eclipse.edc.connector.controlplane.asset.spi.index.DataAddressResolver;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyArchive;
import org.eclipse.edc.connector.controlplane.transfer.spi.TransferProcessPendingGuard;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowController;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessObservable;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessStartedData;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferCompletionMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferProcessAck;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferRemoteMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferRequestMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferStartMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferSuspensionMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferTerminationMessage;
import org.eclipse.edc.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.edc.virtual.controlplane.participantcontext.spi.ParticipantWebhookResolver;
import org.eclipse.edc.virtual.controlplane.tasks.ProcessTaskPayload;
import org.eclipse.edc.virtual.controlplane.tasks.Task;
import org.eclipse.edc.virtual.controlplane.tasks.TaskService;
import org.eclipse.edc.virtual.controlplane.transfer.spi.TransferProcessTaskExecutor;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.CompleteDataFlow;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.PrepareTransfer;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.ResumeDataFlow;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.SendTransferRequest;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.SignalDataflowStarted;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.StartDataflow;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.SuspendDataFlow;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.TerminateDataFlow;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.TransferProcessTaskPayload;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess.Type.CONSUMER;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess.Type.PROVIDER;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.REQUESTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.from;
import static org.eclipse.edc.spi.response.ResponseStatus.FATAL_ERROR;
import static org.eclipse.edc.spi.types.domain.DataAddress.EDC_DATA_ADDRESS_SECRET;

public class TransferProcessTaskExecutorImpl implements TransferProcessTaskExecutor {

    private final Map<Class<? extends TransferProcessTaskPayload>, Handler> handlers = new HashMap<>();
    private TransferProcessStore store;
    private TaskService taskService;
    private TransactionContext transactionContext;
    private DataFlowController dataFlowController;
    private RemoteMessageDispatcherRegistry dispatcherRegistry;
    private ParticipantWebhookResolver webhookResolver;
    private Vault vault;
    private DataAddressResolver addressResolver;
    private TransferProcessObservable observable;
    private PolicyArchive policyArchive;
    private TransferProcessPendingGuard pendingGuard;
    private Monitor monitor;
    private Clock clock = Clock.systemUTC();

    private TransferProcessTaskExecutorImpl() {
        registerStateHandlers();
    }

    private void registerStateHandlers() {
        handlers.put(PrepareTransfer.class, new Handler(this::handlePrepareTransfer, null));
        handlers.put(SendTransferRequest.class, new Handler(this::handleSendRequest, CONSUMER));
        handlers.put(StartDataflow.class, new Handler(this::handleStartDataflow, PROVIDER));
        handlers.put(SignalDataflowStarted.class, new Handler(this::handleSignalStartedDataflow, CONSUMER));
        handlers.put(SuspendDataFlow.class, new Handler(this::handleSuspendDataflow, null));
        handlers.put(ResumeDataFlow.class, new Handler(this::handleResumeDataflow, null));
        handlers.put(TerminateDataFlow.class, new Handler(this::handleTerminateDataflow, null));
        handlers.put(CompleteDataFlow.class, new Handler(this::handleCompleteDataflow, null));
    }

    @Override
    public StatusResult<Void> handle(TransferProcessTaskPayload task) {
        return handleTask(task);
    }

    private void storeTask(TransferProcessTaskPayload payload) {

        var task = Task.Builder.newInstance().at(clock.millis())
                .payload(payload)
                .build();
        taskService.create(task);
    }

    private StatusResult<Void> handleTask(TransferProcessTaskPayload task) {
        var expectedState = TransferProcessStates.valueOf(task.getProcessState());
        var transferId = task.getProcessId();
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

            var handler = handlers.get(task.getClass());
            if (handler == null) {
                monitor.debug("No handler for task '%s' in transfer process with id '%s'".formatted(task.getClass().getSimpleName(), transferId));
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

    private StatusResult<Void> handlePrepareTransfer(TransferProcess process) {
        var contractId = process.getContractId();
        var policy = policyArchive.findPolicyForContract(contractId);

        if (policy == null) {
            transitionToTerminating(process, "Policy not found for contract: " + contractId);
            return StatusResult.failure(FATAL_ERROR, "Policy not found for contract: " + contractId);
        }

        if (process.getType() == CONSUMER) {

            var provisioning = dataFlowController.prepare(process, policy);

            if (provisioning.failed()) {
                // with the upcoming data-plane signaling data-plane will be mandatory also on consumer side
                // so in this case the transfer will be terminated straight away
                transitionToRequesting(process);
            } else {
                var response = provisioning.getContent();
                process.setDataPlaneId(response.getDataPlaneId());
                if (response.isProvisioning()) {
                    process.transitionProvisioningRequested();
                } else {
                    process.updateDestination(response.getDataAddress());
                    transitionToRequesting(process);
                }
            }

        } else {
            var assetId = process.getAssetId();
            var dataAddress = addressResolver.resolveForAsset(assetId);
            if (dataAddress == null) {
                transitionToStarting(process);
                return StatusResult.success();
            }
            // default the content address to the asset address; this may be overridden during provisioning
            process.setContentDataAddress(dataAddress);

            transitionToStarting(process);
        }

        return StatusResult.success();
    }

    private StatusResult<Void> handleStartDataflow(TransferProcess process) {
        var policy = policyArchive.findPolicyForContract(process.getContractId());

        var result = dataFlowController.start(process, policy);

        if (result.failed()) {
            transitionToTerminating(process, result.getFailureDetail());
            return StatusResult.failure(FATAL_ERROR, result.getFailureDetail());
        }
        var dataFlowResponse = result.getContent();
        var messageBuilder = TransferStartMessage.Builder.newInstance().dataAddress(dataFlowResponse.getDataAddress());
        process.setDataPlaneId(dataFlowResponse.getDataPlaneId());
        return dispatch(messageBuilder, process, Object.class)
                .onSuccess(c -> transitionToStarted(process))
                .mapEmpty();
    }

    private StatusResult<Void> handleSignalStartedDataflow(TransferProcess process) {
        return dataFlowController.started(process)
                .onSuccess(v -> transitionToStarted(process));
    }

    private StatusResult<Void> handleSuspendDataflow(TransferProcess process) {
        if (process.getType() == PROVIDER) {
            var result = dataFlowController.suspend(process);
            if (result.failed()) {
                transitionToTerminating(process, "Failed to suspend transfer process: " + result.getFailureDetail());
                return StatusResult.failure(FATAL_ERROR, result.getFailureDetail());
            }
        }
        if (process.suspensionWasRequestedByCounterParty()) {
            transitionToSuspended(process);
            return StatusResult.success();
        } else {
            return dispatch(TransferSuspensionMessage.Builder.newInstance().reason(process.getErrorDetail()), process, Object.class)
                    .onSuccess(c -> transitionToSuspended(process))
                    .mapEmpty();
        }
    }

    private StatusResult<Void> handleResumeDataflow(TransferProcess process) {
        if (process.getType() == CONSUMER) {
            return handleConsumerResumeDataflow(process);
        } else {
            return handleStartDataflow(process);
        }
    }

    private StatusResult<Void> handleConsumerResumeDataflow(TransferProcess process) {
        var messageBuilder = TransferStartMessage.Builder.newInstance();

        return dispatch(messageBuilder, process, Object.class)
                .onSuccess(c -> transitionToResumed(process))
                .mapEmpty();
    }

    private StatusResult<Void> handleTerminateDataflow(TransferProcess process) {
        if (process.getType() == CONSUMER && process.getState() < REQUESTED.code()) {
            transitionToTerminated(process);
            return StatusResult.success();
        }
        var result = dataFlowController.terminate(process);

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

    private StatusResult<Void> handleCompleteDataflow(TransferProcess process) {
        if (process.completionWasRequestedByCounterParty()) {
            var result = dataFlowController.terminate(process);
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

    private StatusResult<Void> handleSendRequest(TransferProcess process) {
        var originalDestination = process.getDataDestination();
        var callbackAddress = webhookResolver.getWebhook(process.getParticipantContextId(), process.getProtocol());
        var agreementId = policyArchive.getAgreementIdForContract(process.getContractId());

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
                    .contractId(agreementId);

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
                .formatted(entity.getType(), entity.getClass().getSimpleName(),
                        entity.getId(), entity.stateAsString()));
    }

    private void transitionToTerminated(TransferProcess process, String message) {
        process.setErrorDetail(message);
        monitor.warning(message);
        transitionToTerminated(process);
    }

    private void transitionToRequesting(TransferProcess process) {
        process.transitionRequesting();
        update(process);

        var task = baseBuilder(SendTransferRequest.Builder.newInstance(), process).build();
        storeTask(task);
    }

    private void transitionToTerminated(TransferProcess process) {
        process.transitionTerminated();
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

        var task = baseBuilder(StartDataflow.Builder.newInstance(), transferProcess).build();
        storeTask(task);
    }

    private void transitionToStarted(TransferProcess transferProcess) {
        transferProcess.transitionStarted();
        update(transferProcess);
        observable.invokeForEach(l -> l.started(transferProcess, TransferProcessStartedData.Builder.newInstance().build()));
    }

    private void transitionToCompleted(TransferProcess transferProcess) {
        transferProcess.transitionCompleted();
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
        update(transferProcess);
        observable.invokeForEach(l -> l.requested(transferProcess));
    }

    private void transitionToResumed(TransferProcess process) {
        process.transitionResumed();
        update(process);
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
            return dispatcherRegistry.dispatch(process.getParticipantContextId(), responseType, message).get();
        } catch (Exception e) {
            return StatusResult.failure(FATAL_ERROR, "Failed to dispatch message: %s".formatted(e.getMessage()));
        }
    }

    protected <T extends ProcessTaskPayload, B extends ProcessTaskPayload.Builder<T, B>> B baseBuilder(B builder, TransferProcess transferProcess) {
        return builder.processId(transferProcess.getId())
                .processState(transferProcess.stateAsString())
                .processType(transferProcess.getType().name());
    }

    private record Handler(Function<TransferProcess, StatusResult<Void>> function, TransferProcess.Type type) {

    }

    public static class Builder {

        private final TransferProcessTaskExecutorImpl manager;

        private Builder() {
            manager = new TransferProcessTaskExecutorImpl();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public TransferProcessTaskExecutor build() {
            Objects.requireNonNull(manager.dataFlowController, "dataFlowController cannot be null");
            Objects.requireNonNull(manager.dispatcherRegistry, "dispatcherRegistry cannot be null");
            Objects.requireNonNull(manager.observable, "observable cannot be null");
            Objects.requireNonNull(manager.policyArchive, "policyArchive cannot be null");
            Objects.requireNonNull(manager.addressResolver, "addressResolver cannot be null");
            Objects.requireNonNull(manager.store, "store");
            Objects.requireNonNull(manager.taskService, "taskService");
            Objects.requireNonNull(manager.monitor, "monitor");
            Objects.requireNonNull(manager.transactionContext, "transactionContext cannot be null");
            return manager;
        }

        public Builder dataFlowController(DataFlowController dataFlowController) {
            manager.dataFlowController = dataFlowController;
            return this;
        }

        public Builder dispatcherRegistry(RemoteMessageDispatcherRegistry registry) {
            manager.dispatcherRegistry = registry;
            return this;
        }

        public Builder vault(Vault vault) {
            manager.vault = vault;
            return this;
        }

        public Builder store(TransferProcessStore store) {
            manager.store = store;
            return this;
        }

        public Builder taskService(TaskService taskService) {
            manager.taskService = taskService;
            return this;
        }

        public Builder transactionContext(TransactionContext transactionContext) {
            manager.transactionContext = transactionContext;
            return this;
        }

        public Builder monitor(Monitor monitor) {
            manager.monitor = monitor;
            return this;
        }

        public Builder observable(TransferProcessObservable observable) {
            manager.observable = observable;
            return this;
        }

        public Builder policyArchive(PolicyArchive policyArchive) {
            manager.policyArchive = policyArchive;
            return this;
        }

        public Builder addressResolver(DataAddressResolver addressResolver) {
            manager.addressResolver = addressResolver;
            return this;
        }

        public Builder webhookResolver(ParticipantWebhookResolver webhookResolver) {
            manager.webhookResolver = webhookResolver;
            return this;
        }

        public Builder pendingGuard(TransferProcessPendingGuard pendingGuard) {
            manager.pendingGuard = pendingGuard;
            return this;
        }

        public Builder clock(Clock clock) {
            manager.clock = clock;
            return this;
        }
    }
}
