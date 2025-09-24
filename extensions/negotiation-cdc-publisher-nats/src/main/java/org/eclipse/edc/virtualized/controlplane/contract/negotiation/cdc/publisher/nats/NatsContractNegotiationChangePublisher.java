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

package org.eclipse.edc.virtualized.controlplane.contract.negotiation.cdc.publisher.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Nats;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationAccepted;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationAgreed;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationEvent;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationInitiated;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationOffered;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationRequested;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationVerified;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.virtualized.controlplane.contract.negotiation.cdc.publisher.nats.ContractNegotiationCdcPublisherExtension.NatsPublisherConfig;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.ContractNegotiationChangeListener;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationAccepting;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationAgreeing;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationFinalizing;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationOffering;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationRequesting;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationTerminating;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationVerifying;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.String.format;
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
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.from;
import static org.eclipse.edc.spi.response.ResponseStatus.FATAL_ERROR;

public class NatsContractNegotiationChangePublisher implements ContractNegotiationChangeListener {

    public final AtomicBoolean active = new AtomicBoolean(false);
    private final NatsPublisherConfig config;
    private final Supplier<ObjectMapper> objectMapper;
    private final Clock clock;
    private final Map<ContractNegotiationStates, Function<ContractNegotiation, ContractNegotiationEvent>> eventTypeMap = new HashMap<>() {
        {
            put(INITIAL, builder(ContractNegotiationInitiated.Builder::newInstance));
            put(REQUESTING, builder(ContractNegotiationRequesting.Builder::newInstance));
            put(REQUESTED, builder(ContractNegotiationRequested.Builder::newInstance));
            put(OFFERING, builder(ContractNegotiationOffering.Builder::newInstance));
            put(OFFERED, builder(ContractNegotiationOffered.Builder::newInstance));
            put(ACCEPTING, builder(ContractNegotiationAccepting.Builder::newInstance));
            put(ACCEPTED, builder(ContractNegotiationAccepted.Builder::newInstance));
            put(AGREEING, builder(ContractNegotiationAgreeing.Builder::newInstance));
            put(AGREED, builder(ContractNegotiationAgreed.Builder::newInstance));
            put(VERIFYING, builder(ContractNegotiationVerifying.Builder::newInstance));
            put(VERIFIED, builder(ContractNegotiationVerified.Builder::newInstance));
            put(FINALIZING, builder(ContractNegotiationFinalizing.Builder::newInstance));
            put(TERMINATING, builder(ContractNegotiationTerminating.Builder::newInstance));
        }
    };
    private JetStream js;
    private Connection connection;

    public NatsContractNegotiationChangePublisher(NatsPublisherConfig config, Supplier<ObjectMapper> objectMapper, Clock clock) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @SuppressWarnings("unchecked")
    @Override
    public StatusResult<Void> onChange(ContractNegotiation before, ContractNegotiation after) {
        if (!active.get()) {
            return StatusResult.failure(FATAL_ERROR, "NATS Contract Negotiation Change Listener is not active.");
        }
        var event = toEvent(after);
        if (event == null) {
            return StatusResult.success();
        }
        var payload = EventEnvelope.Builder.newInstance()
                .payload(event)
                .at(clock.millis())
                .build();
        try {
            var subject = format("%s.%s.%s", config.subjectPrefix(), after.getType().name().toLowerCase(), after.stateAsString().toLowerCase());
            var message = objectMapper.get().writeValueAsString(payload);
            js.publish(subject, message.getBytes());
            return StatusResult.success();
        } catch (Exception e) {
            return StatusResult.failure(FATAL_ERROR, "Failed to publish contract negotiation event: " + e.getMessage());
        }
    }

    public void start() {
        try {
            connection = Nats.connect(config.url());
            js = connection.jetStream();
            active.set(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public void stop() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                throw new RuntimeException("Failed to close NATS connection", e);
            }
        }
        active.set(false);
    }


    private ContractNegotiationEvent toEvent(ContractNegotiation cn) {
        var state = from(cn.getState());
        return Optional.ofNullable(eventTypeMap.get(state))
                .map(builder -> builder.apply(cn))
                .orElse(null);
    }

    private <T extends ContractNegotiationEvent, B extends ContractNegotiationEvent.Builder<T, B>> Function<ContractNegotiation, ContractNegotiationEvent> builder(Supplier<B> builder) {
        return negotiation -> {
            var b = baseBuilder(builder.get(), negotiation);
            return b.build();
        };
    }

    private <T extends ContractNegotiationEvent, B extends ContractNegotiationEvent.Builder<T, B>> B baseBuilder(B builder, ContractNegotiation negotiation) {
        return builder.contractNegotiationId(negotiation.getId())
                .counterPartyAddress(negotiation.getCounterPartyAddress())
                .callbackAddresses(negotiation.getCallbackAddresses())
                .contractOffers(negotiation.getContractOffers())
                .counterPartyId(negotiation.getCounterPartyId())
                .protocol(negotiation.getProtocol());
    }


}
