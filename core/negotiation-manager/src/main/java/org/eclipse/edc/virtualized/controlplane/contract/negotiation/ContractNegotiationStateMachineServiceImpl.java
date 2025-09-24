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

package org.eclipse.edc.virtualized.controlplane.contract.negotiation;

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.ContractNegotiationPendingGuard;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.observe.ContractNegotiationObservable;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreementMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreementVerificationMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractNegotiationEventMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationTerminationMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractOfferMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequestMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.protocol.ContractNegotiationAck;
import org.eclipse.edc.policy.model.PolicyType;
import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.types.domain.message.ProcessRemoteMessage;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.ContractNegotiationStateMachineService;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.ACCEPTED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.ACCEPTING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.AGREEING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.FINALIZING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.INITIAL;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.OFFERING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.REQUESTED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.REQUESTING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.TERMINATING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.VERIFIED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.VERIFYING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.from;
import static org.eclipse.edc.spi.response.ResponseStatus.FATAL_ERROR;

public class ContractNegotiationStateMachineServiceImpl implements ContractNegotiationStateMachineService {

    private final String participantId;
    private final Clock clock;
    private final DataspaceProfileContextRegistry dataspaceProfileContextRegistry;
    private final RemoteMessageDispatcherRegistry dispatcherRegistry;
    private final ContractNegotiationStore store;
    private final TransactionContext transactionContext;
    private final ContractNegotiationPendingGuard pendingGuard;
    private final ContractNegotiationObservable observable;
    private final Monitor monitor;


    private final Map<ContractNegotiationStates, Handler> stateHandlers = new HashMap<>();

    public ContractNegotiationStateMachineServiceImpl(String participantId, Clock clock,
                                                      DataspaceProfileContextRegistry dataspaceProfileContextRegistry,
                                                      RemoteMessageDispatcherRegistry dispatcherRegistry,
                                                      ContractNegotiationStore store,
                                                      TransactionContext transactionContext,
                                                      ContractNegotiationPendingGuard pendingGuard,
                                                      ContractNegotiationObservable observable,
                                                      Monitor monitor) {
        this.participantId = participantId;
        this.clock = clock;
        this.dataspaceProfileContextRegistry = dataspaceProfileContextRegistry;
        this.dispatcherRegistry = dispatcherRegistry;
        this.store = store;
        this.transactionContext = transactionContext;
        this.pendingGuard = pendingGuard;
        this.observable = observable;
        this.monitor = monitor;
        registerStateHandlers();
    }

    private void registerStateHandlers() {
        stateHandlers.put(REQUESTED, new Handler(this::processRequested, ContractNegotiation.Type.PROVIDER));
        stateHandlers.put(REQUESTING, new Handler(this::handleRequesting, ContractNegotiation.Type.CONSUMER));
        stateHandlers.put(OFFERING, new Handler(this::handleOffering, ContractNegotiation.Type.PROVIDER));
        stateHandlers.put(TERMINATING, new Handler(this::processTerminating, null));
        stateHandlers.put(VERIFIED, new Handler(this::processVerified, ContractNegotiation.Type.PROVIDER));
        stateHandlers.put(VERIFYING, new Handler(this::processVerifying, ContractNegotiation.Type.CONSUMER));
        stateHandlers.put(ACCEPTING, new Handler(this::processAccepting, ContractNegotiation.Type.CONSUMER));
        stateHandlers.put(ACCEPTED, new Handler(this::processAccepted, ContractNegotiation.Type.PROVIDER));
        stateHandlers.put(AGREEING, new Handler(this::processAgreeing, ContractNegotiation.Type.PROVIDER));
        stateHandlers.put(INITIAL, new Handler(this::processInitial, ContractNegotiation.Type.CONSUMER));
        stateHandlers.put(FINALIZING, new Handler(this::processFinalizing, ContractNegotiation.Type.PROVIDER));
    }

    @Override
    public StatusResult<Void> handle(String negotiationId, ContractNegotiationStates state) {
        return handleEvent(negotiationId, state);
    }

    private StatusResult<Void> handleEvent(String negotiationId, ContractNegotiationStates expectedState) {
        return transactionContext.execute(() -> {
            var negotiationResult = loadNegotiation(negotiationId);
            if (negotiationResult.failed()) {
                return StatusResult.failure(FATAL_ERROR, negotiationResult.getFailureDetail());
            }

            var negotiation = negotiationResult.getContent();
            if (ContractNegotiationStates.isFinal(negotiation.getState())) {
                monitor.debug("Skipping contract negotiation with id '%s' is in final state '%s'".formatted(negotiationId, from(negotiation.getState())));
                return StatusResult.success();
            }

            if (negotiation.getState() != expectedState.code()) {
                monitor.warning("Skipping contract negotiation with id '%s' is in state '%s', expected '%s'".formatted(negotiationId, from(negotiation.getState()), expectedState));
                return StatusResult.success();
            }

            var handler = stateHandlers.get(expectedState);
            if (handler == null) {
                monitor.debug("No handler for state '%s' in contract negotiation with id '%s'".formatted(expectedState, negotiationId));
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

            return handler.function.apply(negotiation);
        });

    }

    private StatusResult<Void> processRequested(ContractNegotiation negotiation) {
        transitionToAgreeing(negotiation);
        return StatusResult.success();
    }

    private StatusResult<Void> handleRequesting(ContractNegotiation negotiation) {
        var callbackAddress = dataspaceProfileContextRegistry.getWebhook(negotiation.getProtocol());

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

    private StatusResult<Void> handleOffering(ContractNegotiation negotiation) {
        var callbackAddress = dataspaceProfileContextRegistry.getWebhook(negotiation.getProtocol());
        if (callbackAddress == null) {
            transitionToTerminated(negotiation, "No callback address found for protocol: %s".formatted(negotiation.getProtocol()));
            return StatusResult.failure(FATAL_ERROR, "No callback address found for protocol: %s".formatted(negotiation.getProtocol()));
        }

        var messageBuilder = ContractOfferMessage.Builder.newInstance()
                .contractOffer(negotiation.getLastContractOffer())
                .callbackAddress(callbackAddress.url());

        return dispatch(messageBuilder, negotiation, ContractNegotiationAck.class)
                .onSuccess(ack -> transitionToOffered(negotiation, ack))
                .mapEmpty();
    }

    protected StatusResult<Void> processTerminating(ContractNegotiation negotiation) {
        var messageBuilder = ContractNegotiationTerminationMessage.Builder.newInstance()
                .rejectionReason(negotiation.getErrorDetail())
                .policy(negotiation.getLastContractOffer().getPolicy());

        return dispatch(messageBuilder, negotiation, Object.class)
                .onSuccess(v -> transitionToTerminated(negotiation))
                .mapEmpty();
    }

    private StatusResult<Void> processVerified(ContractNegotiation negotiation) {
        transitionToFinalizing(negotiation);
        return StatusResult.success();
    }

    protected StatusResult<Void> processVerifying(ContractNegotiation negotiation) {
        var messageBuilder = ContractAgreementVerificationMessage.Builder.newInstance()
                .policy(negotiation.getContractAgreement().getPolicy());

        return dispatch(messageBuilder, negotiation, Object.class)
                .onSuccess((n) -> transitionToVerified(negotiation))
                .mapEmpty();
    }

    protected StatusResult<Void> processAccepting(ContractNegotiation negotiation) {
        var messageBuilder = ContractNegotiationEventMessage.Builder.newInstance().type(ContractNegotiationEventMessage.Type.ACCEPTED);
        messageBuilder.policy(negotiation.getLastContractOffer().getPolicy());
        return dispatch(messageBuilder, negotiation, Object.class)
                .onSuccess((n) -> transitionToAccepted(negotiation))
                .mapEmpty();
    }

    private StatusResult<Void> processAccepted(ContractNegotiation negotiation) {
        transitionToAgreeing(negotiation);
        return StatusResult.success();
    }

    protected StatusResult<Void> processAgreeing(ContractNegotiation negotiation) {
        var callbackAddress = dataspaceProfileContextRegistry.getWebhook(negotiation.getProtocol());
        if (callbackAddress == null) {
            transitionToTerminated(negotiation, "No callback address found for protocol: %s".formatted(negotiation.getProtocol()));
            return StatusResult.failure(FATAL_ERROR, "No callback address found for protocol: %s".formatted(negotiation.getProtocol()));
        }

        var agreement = Optional.ofNullable(negotiation.getContractAgreement())
                .orElseGet(() -> {
                    var lastOffer = negotiation.getLastContractOffer();

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
                            .build();
                });

        var messageBuilder = ContractAgreementMessage.Builder.newInstance()
                .callbackAddress(callbackAddress.url())
                .contractAgreement(agreement);

        return dispatch(messageBuilder, negotiation, Object.class)
                .onSuccess(v -> transitionToAgreed(negotiation, agreement))
                .mapEmpty();
    }

    private StatusResult<Void> processInitial(ContractNegotiation negotiation) {
        transitionToRequesting(negotiation);
        return StatusResult.success();
    }

    private StatusResult<Void> processFinalizing(ContractNegotiation negotiation) {
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

    protected void transitionToAccepted(ContractNegotiation negotiation) {
        negotiation.transitionAccepted();
        update(negotiation);
        observable.invokeForEach(l -> l.accepted(negotiation));
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
            return dispatcherRegistry.dispatch(responseType, message).get();
        } catch (Exception e) {
            return StatusResult.failure(FATAL_ERROR, "Failed to dispatch message: %s".formatted(e.getMessage()));
        }
    }

    protected void transitionToRequesting(ContractNegotiation negotiation) {
        negotiation.transitionRequesting();
        update(negotiation);
    }

    protected void transitionToOffered(ContractNegotiation negotiation, ContractNegotiationAck ack) {
        negotiation.transitionOffered();
        negotiation.setCorrelationId(ack.getConsumerPid());
        update(negotiation);
        observable.invokeForEach(l -> l.offered(negotiation));
    }

    protected void update(ContractNegotiation entity) {
        store.save(entity);
        monitor.debug(() -> "[%s] %s %s is now in state %s"
                .formatted(this.getClass().getSimpleName(), entity.getClass().getSimpleName(),
                        entity.getId(), entity.stateAsString()));
    }

    private record Handler(Function<ContractNegotiation, StatusResult<Void>> function, ContractNegotiation.Type type) {

    }
}
