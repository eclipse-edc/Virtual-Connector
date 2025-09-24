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

package org.eclipse.edc.virtualized.controlplane.contract.negotiation.subscriber;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Nats;
import io.nats.client.PullSubscribeOptions;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationAccepted;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationAgreed;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationInitiated;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationOffered;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationRequested;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationVerified;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.virtualized.controlplane.contract.negotiation.subscriber.NatsContractNegotiationSubscriberExtension.NatsSubscriberConfig;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.ContractNegotiationStateMachineService;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationAccepting;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationAgreeing;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationFinalizing;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationOffering;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationRequesting;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationTerminating;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationVerifying;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
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

public class NatsContractNegotiationSubscriber {

    private final NatsSubscriberConfig config;
    private final ContractNegotiationStateMachineService stateMachineService;
    private final Supplier<ObjectMapper> mapperSupplier;
    private final Monitor monitor;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean active = new AtomicBoolean(false);

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

    private Connection connection;

    public NatsContractNegotiationSubscriber(NatsSubscriberConfig config, ContractNegotiationStateMachineService stateMachineService, Supplier<ObjectMapper> mapperSupplier, Monitor monitor) {
        this.config = config;
        this.stateMachineService = stateMachineService;
        this.mapperSupplier = mapperSupplier;
        this.monitor = monitor;
    }

    public void start() {
        try {
            connection = Nats.connect(config.url());
            var js = connection.jetStream();
            var pullOptions = PullSubscribeOptions.builder()
                    .durable(config.name())
                    .build();

            var sub = js.subscribe(config.subject(), pullOptions);
            active.set(true);
            executorService.submit(() -> {
                run(sub);
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void run(JetStreamSubscription sub) {
        while (active.get()) {
            var messages = sub.fetch(100, Duration.ofMillis(100));
            for (var message : messages) {
                try {
                    var envelope = mapperSupplier.get().readValue(message.getData(), new TypeReference<Map<String, Object>>() {
                    });
                    var contractNegotiationId = getContractNegotiationId(envelope);
                    var state = getState(envelope);
                    var result = stateMachineService.handle(contractNegotiationId, state);
                    if (result.failed()) {
                        monitor.severe("Failed to handle contract negotiation state change for ID: " + contractNegotiationId + ", state: " + state + ", reason: " + result.getFailureDetail());
                        message.nak();
                        continue;
                    }
                    message.ack();
                } catch (Exception e) {
                    monitor.severe("Failed to process contract negotiation message: " + e.getMessage(), e);
                    message.nak();
                }
            }
        }
    }

    public void stop() {
        active.set(false);
        executorService.shutdown();
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
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
}
