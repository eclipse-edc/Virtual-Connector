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

package org.eclipse.edc.virtual.controlplane.contract.negotiation.task;

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.ContractNegotiationPendingGuard;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.observe.ContractNegotiationObservable;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreementMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreementVerificationMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractNegotiationEventMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequestMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.protocol.ContractNegotiationAck;
import org.eclipse.edc.participantcontext.spi.identity.ParticipantIdentityResolver;
import org.eclipse.edc.policy.model.PolicyType;
import org.eclipse.edc.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.types.domain.message.ProcessRemoteMessage;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.ContractNegotiationTaskExecutor;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.AgreeNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.ContractNegotiationTaskPayload;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.FinalizeNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.RequestNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.SendAgreement;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.SendFinalizeNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.SendRequestNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.SendVerificationNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.VerifyNegotiation;
import org.eclipse.edc.virtual.controlplane.participantcontext.spi.ParticipantWebhookResolver;
import org.eclipse.edc.virtual.controlplane.tasks.ProcessTaskPayload;
import org.eclipse.edc.virtual.controlplane.tasks.Task;
import org.eclipse.edc.virtual.controlplane.tasks.TaskService;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.from;
import static org.eclipse.edc.spi.response.ResponseStatus.FATAL_ERROR;

public class ContractNegotiationTaskExecutorImpl implements ContractNegotiationTaskExecutor {

    private final Map<Class<? extends ContractNegotiationTaskPayload>, Handler> handlers = new HashMap<>();
    protected Clock clock;
    protected ParticipantIdentityResolver identityResolver;
    protected ParticipantWebhookResolver webhookResolver;
    protected RemoteMessageDispatcherRegistry dispatcherRegistry;
    protected ContractNegotiationStore store;
    protected TransactionContext transactionContext;
    protected ContractNegotiationPendingGuard pendingGuard;
    protected ContractNegotiationObservable observable;
    protected Monitor monitor;
    protected TaskService taskService;

    private ContractNegotiationTaskExecutorImpl() {
        registerStateHandlers();
    }

    private void registerStateHandlers() {
        handlers.put(RequestNegotiation.class, new Handler(this::handleRequest, ContractNegotiation.Type.CONSUMER));
        handlers.put(SendRequestNegotiation.class, new Handler(this::handleSendRequest, ContractNegotiation.Type.CONSUMER));
        handlers.put(AgreeNegotiation.class, new Handler(this::handleAgree, ContractNegotiation.Type.PROVIDER));
        handlers.put(SendAgreement.class, new Handler(this::handleSendAgreement, ContractNegotiation.Type.PROVIDER));
        handlers.put(VerifyNegotiation.class, new Handler(this::handleVerify, ContractNegotiation.Type.CONSUMER));
        handlers.put(SendVerificationNegotiation.class, new Handler(this::handleSendVerification, ContractNegotiation.Type.CONSUMER));
        handlers.put(FinalizeNegotiation.class, new Handler(this::handleFinalize, ContractNegotiation.Type.PROVIDER));
        handlers.put(SendFinalizeNegotiation.class, new Handler(this::handleSendFinalize, ContractNegotiation.Type.PROVIDER));
    }

    @Override
    public StatusResult<Void> handle(ContractNegotiationTaskPayload task) {
        return handleTask(task);
    }

    private void storeTask(ContractNegotiationTaskPayload payload) {
        var task = Task.Builder.newInstance().at(clock.millis())
                .payload(payload)
                .build();
        taskService.create(task);
    }

    private StatusResult<Void> handleTask(ContractNegotiationTaskPayload task) {
        var expectedState = ContractNegotiationStates.valueOf(task.getProcessState());
        var negotiationId = task.getProcessId();
        return transactionContext.execute(() -> {
            var negotiationResult = loadNegotiation(task.getProcessId());
            if (negotiationResult.failed()) {
                return StatusResult.failure(FATAL_ERROR, negotiationResult.getFailureDetail());
            }

            var negotiation = negotiationResult.getContent();
            if (ContractNegotiationStates.isFinal(negotiation.getState())) {
                monitor.debug("Skipping contract negotiation with id '%s' is in final state '%s'".formatted(task.getProcessId(), from(negotiation.getState())));
                return StatusResult.success();
            }

            if (negotiation.getState() != expectedState.code()) {
                monitor.warning("Skipping contract negotiation with id '%s' is in state '%s', expected '%s'".formatted(negotiationId, from(negotiation.getState()), expectedState));
                return StatusResult.success();
            }

            var handler = handlers.get(task.getClass());
            if (handler == null) {
                monitor.debug("No handler for task '%s' in contract negotiation with id '%s'".formatted(task.getClass().getSimpleName(), negotiationId));
                return StatusResult.success();
            }

            if (handler.type != null && handler.type != negotiation.getType()) {
                monitor.debug("Skipping '%s' for contract negotiation with id '%s' due to type mismatch: expected '%s', got '%s'".formatted(expectedState, negotiationId, handler.type, negotiation.getType()));
                return StatusResult.success();
            }

            if (pendingGuard.test(negotiation)) {
                monitor.debug("Skipping '%s' for contract negotiation with id '%s' due matched guard".formatted(expectedState, negotiationId));
                return StatusResult.success();
            }

            var result = handler.function.apply(negotiation);
            if (result.fatalError()) {
                monitor.severe("Processing contract negotiation with id '%s' in state '%s' failed: %s"
                        .formatted(task.getProcessId(), expectedState, result.getFailureDetail()));
                transitionToTerminated(negotiation, "Fatal error during processing: %s".formatted(result.getFailureDetail()));
                return StatusResult.success();
            } else {
                return result;

            }

        });

    }

    private StatusResult<Void> handleAgree(ContractNegotiation negotiation) {
        transitionToAgreeing(negotiation);
        var task = baseBuilder(SendAgreement.Builder.newInstance(), negotiation)
                .build();
        storeTask(task);
        return StatusResult.success();
    }

    private StatusResult<Void> handleSendRequest(ContractNegotiation negotiation) {
        var callbackAddress = webhookResolver.getWebhook(negotiation.getParticipantContextId(), negotiation.getProtocol());

        if (callbackAddress != null) {
            var type = ContractRequestMessage.Type.INITIAL;
            if (negotiation.getContractOffers().size() > 1) {
                type = ContractRequestMessage.Type.COUNTER_OFFER;
            }
            var messageBuilder = ContractRequestMessage.Builder.newInstance()
                    .contractOffer(negotiation.getLastContractOffer())
                    .callbackAddress(callbackAddress.url())
                    .type(type);

            return dispatch(messageBuilder, negotiation, ContractNegotiationAck.class)
                    .onSuccess(ack -> transitionToRequested(negotiation, ack))
                    .mapEmpty();
        } else {
            transitionToTerminated(negotiation, "No callback address found for protocol: %s".formatted(negotiation.getProtocol()));
            return StatusResult.success();
        }
    }

    private StatusResult<Void> handleFinalize(ContractNegotiation negotiation) {
        transitionToFinalizing(negotiation);
        var task = baseBuilder(SendFinalizeNegotiation.Builder.newInstance(), negotiation)
                .build();
        storeTask(task);
        return StatusResult.success();
    }

    protected StatusResult<Void> handleSendVerification(ContractNegotiation negotiation) {
        var messageBuilder = ContractAgreementVerificationMessage.Builder.newInstance()
                .policy(negotiation.getContractAgreement().getPolicy());

        return dispatch(messageBuilder, negotiation, Object.class)
                .onSuccess((n) -> transitionToVerified(negotiation))
                .mapEmpty();
    }

    protected StatusResult<Void> handleSendAgreement(ContractNegotiation negotiation) {
        var callbackAddress = webhookResolver.getWebhook(negotiation.getParticipantContextId(), negotiation.getProtocol());
        if (callbackAddress == null) {
            transitionToTerminated(negotiation, "No callback address found for protocol: %s".formatted(negotiation.getProtocol()));
            return StatusResult.failure(FATAL_ERROR, "No callback address found for protocol: %s".formatted(negotiation.getProtocol()));
        }

        var agreement = Optional.ofNullable(negotiation.getContractAgreement())
                .orElseGet(() -> {
                    var lastOffer = negotiation.getLastContractOffer();

                    var participantId = identityResolver.getParticipantId(negotiation.getParticipantContextId(), negotiation.getProtocol());

                    var contractPolicy = lastOffer.getPolicy().toBuilder().type(PolicyType.CONTRACT)
                            .assignee(negotiation.getCounterPartyId())
                            .assigner(participantId)
                            .build();

                    return ContractAgreement.Builder.newInstance()
                            .contractSigningDate(clock.instant().getEpochSecond())
                            .providerId(participantId)
                            .consumerId(negotiation.getCounterPartyId())
                            .policy(contractPolicy)
                            .assetId(lastOffer.getAssetId())
                            .participantContextId(negotiation.getParticipantContextId())
                            .build();
                });

        var messageBuilder = ContractAgreementMessage.Builder.newInstance()
                .callbackAddress(callbackAddress.url())
                .contractAgreement(agreement);

        return dispatch(messageBuilder, negotiation, Object.class)
                .onSuccess(v -> transitionToAgreed(negotiation, agreement))
                .mapEmpty();
    }

    private StatusResult<Void> handleVerify(ContractNegotiation negotiation) {
        transitionToVerifying(negotiation);
        var task = baseBuilder(SendVerificationNegotiation.Builder.newInstance(), negotiation)
                .build();
        storeTask(task);
        return StatusResult.success();
    }

    private StatusResult<Void> handleRequest(ContractNegotiation negotiation) {
        transitionToRequesting(negotiation);
        var task = baseBuilder(SendRequestNegotiation.Builder.newInstance(), negotiation)
                .build();
        storeTask(task);
        return StatusResult.success();
    }

    private StatusResult<Void> handleSendFinalize(ContractNegotiation negotiation) {
        var messageBuilder = ContractNegotiationEventMessage.Builder.newInstance()
                .type(ContractNegotiationEventMessage.Type.FINALIZED)
                .policy(negotiation.getContractAgreement().getPolicy());

        return dispatch(messageBuilder, negotiation, Object.class)
                .onSuccess((n) -> transitionToFinalized(negotiation))
                .mapEmpty();
    }

    protected void transitionToTerminated(ContractNegotiation negotiation, String message) {
        negotiation.setErrorDetail(message);
        transitionToTerminated(negotiation);
        observable.invokeForEach(l -> l.terminated(negotiation));
    }

    protected void transitionToAgreeing(ContractNegotiation negotiation) {
        negotiation.transitionAgreeing();
        update(negotiation);
    }

    protected void transitionToTerminated(ContractNegotiation negotiation) {
        negotiation.transitionTerminated();
        update(negotiation);
        observable.invokeForEach(l -> l.terminated(negotiation));
    }

    protected void transitionToAgreed(ContractNegotiation negotiation, ContractAgreement agreement) {
        negotiation.setContractAgreement(agreement);
        negotiation.transitionAgreed();
        update(negotiation);
        observable.invokeForEach(l -> l.agreed(negotiation));
    }

    protected void transitionToFinalized(ContractNegotiation negotiation) {
        negotiation.transitionFinalized();
        update(negotiation);
        observable.invokeForEach(l -> l.finalized(negotiation));
    }

    protected void transitionToFinalizing(ContractNegotiation negotiation) {
        negotiation.transitionFinalizing();
        update(negotiation);
    }

    protected void transitionToRequested(ContractNegotiation negotiation, ContractNegotiationAck ack) {
        negotiation.transitionRequested();
        negotiation.setCorrelationId(ack.getProviderPid());
        update(negotiation);
        observable.invokeForEach(l -> l.requested(negotiation));
    }

    protected void transitionToVerifying(ContractNegotiation negotiation) {
        negotiation.transitionVerifying();
        update(negotiation);
    }

    protected void transitionToVerified(ContractNegotiation negotiation) {
        negotiation.transitionVerified();
        update(negotiation);
        observable.invokeForEach(l -> l.verified(negotiation));
    }

    private StatusResult<ContractNegotiation> loadNegotiation(String negotiationId) {
        var negotiation = store.findById(negotiationId);
        if (negotiation == null) {
            return StatusResult.failure(FATAL_ERROR, "Contract negotiation with id '%s' not found".formatted(negotiationId));
        }
        return StatusResult.success(negotiation);
    }

    protected <T> StatusResult<T> dispatch(
            ProcessRemoteMessage.Builder<?, ?> messageBuilder,
            ContractNegotiation negotiation, Class<T> responseType) {
        messageBuilder.counterPartyAddress(negotiation.getCounterPartyAddress())
                .counterPartyId(negotiation.getCounterPartyId())
                .protocol(negotiation.getProtocol())
                .processId(Optional.ofNullable(negotiation.getCorrelationId()).orElse(negotiation.getId()));

        if (negotiation.getType() == ContractNegotiation.Type.CONSUMER) {
            messageBuilder.consumerPid(negotiation.getId()).providerPid(negotiation.getCorrelationId());
        } else {
            messageBuilder.providerPid(negotiation.getId()).consumerPid(negotiation.getCorrelationId());
        }

        if (negotiation.lastSentProtocolMessage() != null) {
            messageBuilder.id(negotiation.lastSentProtocolMessage());
        }

        var message = messageBuilder.build();

        negotiation.lastSentProtocolMessage(message.getId());

        try {
            return dispatcherRegistry.dispatch(negotiation.getParticipantContextId(), responseType, message).get();
        } catch (Exception e) {
            return StatusResult.failure(FATAL_ERROR, "Failed to dispatch message: %s".formatted(e.getMessage()));
        }
    }

    protected void transitionToRequesting(ContractNegotiation negotiation) {
        negotiation.transitionRequesting();
        update(negotiation);
    }
    
    protected void update(ContractNegotiation entity) {
        store.save(entity);
        monitor.debug(() -> "[%s] %s %s is now in state %s".formatted(entity.getType(), entity.getClass().getSimpleName(),
                entity.getId(), entity.stateAsString()));
    }

    protected <T extends ProcessTaskPayload, B extends ProcessTaskPayload.Builder<T, B>> B baseBuilder(B builder, ContractNegotiation negotiation) {
        return builder.processId(negotiation.getId())
                .processState(negotiation.stateAsString())
                .processType(negotiation.getType().name());
    }

    private record Handler(Function<ContractNegotiation, StatusResult<Void>> function, ContractNegotiation.Type type) {

    }

    public static class Builder {
        private final ContractNegotiationTaskExecutorImpl manager;

        private Builder() {
            manager = new ContractNegotiationTaskExecutorImpl();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder taskService(TaskService taskStore) {
            this.manager.taskService = taskStore;
            return this;
        }

        public Builder clock(Clock clock) {
            this.manager.clock = clock;
            return this;
        }

        public Builder identityResolver(ParticipantIdentityResolver identityResolver) {
            this.manager.identityResolver = identityResolver;
            return this;
        }

        public Builder webhookResolver(ParticipantWebhookResolver webhookResolver) {
            this.manager.webhookResolver = webhookResolver;
            return this;
        }

        public Builder dispatcherRegistry(RemoteMessageDispatcherRegistry dispatcherRegistry) {
            this.manager.dispatcherRegistry = dispatcherRegistry;
            return this;
        }

        public Builder store(ContractNegotiationStore store) {
            this.manager.store = store;
            return this;
        }

        public Builder transactionContext(TransactionContext transactionContext) {
            this.manager.transactionContext = transactionContext;
            return this;
        }

        public Builder pendingGuard(ContractNegotiationPendingGuard pendingGuard) {
            this.manager.pendingGuard = pendingGuard;
            return this;
        }

        public Builder observable(ContractNegotiationObservable observable) {
            this.manager.observable = observable;
            return this;
        }

        public Builder monitor(Monitor monitor) {
            this.manager.monitor = monitor;
            return this;
        }

        public ContractNegotiationTaskExecutor build() {
            java.util.Objects.requireNonNull(manager.taskService, "taskStore must not be null");
            java.util.Objects.requireNonNull(manager.clock, "clock must not be null");
            java.util.Objects.requireNonNull(manager.identityResolver, "identityResolver must not be null");
            java.util.Objects.requireNonNull(manager.webhookResolver, "webhookResolver must not be null");
            java.util.Objects.requireNonNull(manager.dispatcherRegistry, "dispatcherRegistry must not be null");
            java.util.Objects.requireNonNull(manager.store, "store must not be null");
            java.util.Objects.requireNonNull(manager.transactionContext, "transactionContext must not be null");
            java.util.Objects.requireNonNull(manager.pendingGuard, "pendingGuard must not be null");
            java.util.Objects.requireNonNull(manager.observable, "observable must not be null");
            java.util.Objects.requireNonNull(manager.monitor, "monitor must not be null");

            return manager;
        }
    }


}
