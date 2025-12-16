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

package org.eclipse.edc.virtual.controlplane.contract.negotiation.subscriber;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Message;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationAccepted;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationAgreed;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationInitiated;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationOffered;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationRequested;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationVerified;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.ContractNegotiationStateMachineService;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.events.ContractNegotiationAccepting;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.events.ContractNegotiationAgreeing;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.events.ContractNegotiationFinalizing;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.events.ContractNegotiationOffering;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.events.ContractNegotiationRequesting;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.events.ContractNegotiationTerminating;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.events.ContractNegotiationVerifying;
import org.eclipse.edc.virtual.nats.subscriber.NatsSubscriber;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.ACCEPTED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.ACCEPTING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.AGREED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.AGREEING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.FINALIZING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.INITIAL;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.OFFERED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.OFFERING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.REQUESTED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.REQUESTING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.TERMINATING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.VERIFIED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.VERIFYING;

public class NatsContractNegotiationSubscriber extends NatsSubscriber {

    private final Map<String, ContractNegotiationStates> stateMap = new HashMap<>() {
        {
            put(ContractNegotiationInitiated.class.getSimpleName(), INITIAL);
            put(ContractNegotiationRequested.class.getSimpleName(), REQUESTED);
            put(ContractNegotiationRequesting.class.getSimpleName(), REQUESTING);
            put(ContractNegotiationOffering.class.getSimpleName(), OFFERING);
            put(ContractNegotiationOffered.class.getSimpleName(), OFFERED);
            put(ContractNegotiationAccepted.class.getSimpleName(), ACCEPTED);
            put(ContractNegotiationAccepting.class.getSimpleName(), ACCEPTING);
            put(ContractNegotiationVerified.class.getSimpleName(), VERIFIED);
            put(ContractNegotiationVerifying.class.getSimpleName(), VERIFYING);
            put(ContractNegotiationAgreed.class.getSimpleName(), AGREED);
            put(ContractNegotiationAgreeing.class.getSimpleName(), AGREEING);
            put(ContractNegotiationFinalizing.class.getSimpleName(), FINALIZING);
            put(ContractNegotiationTerminating.class.getSimpleName(), TERMINATING);
        }
    };
    protected ContractNegotiationStateMachineService stateMachineService;
    protected Supplier<ObjectMapper> mapperSupplier;

    private NatsContractNegotiationSubscriber() {
    }

    @Override
    protected StatusResult<Void> handleMessage(Message message) {
        try {
            var envelope = mapperSupplier.get().readValue(message.getData(), new TypeReference<Map<String, Object>>() {
            });
            var contractNegotiationId = getContractNegotiationId(envelope);
            var state = getState(envelope);
            return stateMachineService.handle(contractNegotiationId, state);
        } catch (Exception e) {
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, e.getMessage());
        }
    }

    private ContractNegotiationStates getState(Map<String, Object> envelope) {
        var state = (String) envelope.get("type");
        return stateMap.get(state);
    }

    @SuppressWarnings("unchecked")
    private String getContractNegotiationId(Map<String, Object> envelope) {
        var payload = (Map<String, Object>) envelope.get("payload");
        return payload.get("contractNegotiationId").toString();
    }

    public static class Builder extends NatsSubscriber.Builder<NatsContractNegotiationSubscriber, Builder> {

        protected Builder() {
            super(new NatsContractNegotiationSubscriber());
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder mapperSupplier(Supplier<ObjectMapper> mapperSupplier) {
            subscriber.mapperSupplier = mapperSupplier;
            return self();
        }

        public Builder stateMachineService(ContractNegotiationStateMachineService stateMachineService) {
            subscriber.stateMachineService = stateMachineService;
            return self();
        }

        @Override
        public Builder self() {
            return this;
        }

        @Override
        public NatsContractNegotiationSubscriber build() {
            Objects.requireNonNull(subscriber.mapperSupplier, "mapperSupplier must be set");
            Objects.requireNonNull(subscriber.stateMachineService, "stateMachineService must be set");
            return super.build();
        }
    }
}
