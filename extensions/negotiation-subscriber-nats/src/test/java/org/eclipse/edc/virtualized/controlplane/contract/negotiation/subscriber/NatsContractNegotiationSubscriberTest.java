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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationAccepted;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationAgreed;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationInitiated;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationOffered;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationRequested;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationVerified;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.virtualized.controlplane.contract.negotiation.subscriber.NatsContractNegotiationSubscriberExtension.NatsSubscriberConfig;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.ContractNegotiationStateMachineService;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationAccepting;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationAgreeing;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationFinalizing;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationOffering;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationRequesting;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationTerminating;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.events.ContractNegotiationVerifying;
import org.eclipse.edc.virtualized.nats.testfixtures.NatsEndToEndExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.UUID;
import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;
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
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NatsContractNegotiationSubscriberTest {

    public static final String STREAM_NAME = "stream_test";
    public static final String CONSUMER_NAME = "consumer_test";
    @Order(0)
    @RegisterExtension
    static final NatsEndToEndExtension NATS_EXTENSION = new NatsEndToEndExtension();
    private final ContractNegotiationStateMachineService stateMachineService = mock();
    private NatsContractNegotiationSubscriber subscriber;

    @BeforeEach
    void beforeEach() {
        NATS_EXTENSION.createStream(STREAM_NAME, "negotiations.>");
        NATS_EXTENSION.createConsumer(STREAM_NAME, CONSUMER_NAME, "negotiations.>");
        var config = new NatsSubscriberConfig(NATS_EXTENSION.getNatsUrl(), CONSUMER_NAME, "negotiations.>");
        subscriber = new NatsContractNegotiationSubscriber(config, stateMachineService, ObjectMapper::new, mock());

    }

    @AfterEach
    void afterEach() {
        subscriber.stop();
        NATS_EXTENSION.deleteStream(STREAM_NAME);
    }

    @ParameterizedTest
    @ArgumentsSource(StateProvider.class)
    void handleMessage(ContractNegotiationStates expectedState, String eventType) {
        when(stateMachineService.handle(any(), any())).thenReturn(StatusResult.success());
        subscriber.start();
        var id = UUID.randomUUID().toString();
        var payload = """
                {
                    "type": "%s",
                    "payload": {
                        "contractNegotiationId": "%s"
                    }
                }
                """.formatted(eventType, id);

        NATS_EXTENSION.publish("negotiations." + eventType.toLowerCase(), payload.getBytes());

        await().untilAsserted(() -> {
            verify(stateMachineService).handle(id, expectedState);
        });
    }


    public static class StateProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {

            return Stream.of(
                    arguments(INITIAL, ContractNegotiationInitiated.class.getSimpleName()),
                    arguments(REQUESTING, ContractNegotiationRequesting.class.getSimpleName()),
                    arguments(REQUESTED, ContractNegotiationRequested.class.getSimpleName()),
                    arguments(OFFERING, ContractNegotiationOffering.class.getSimpleName()),
                    arguments(OFFERED, ContractNegotiationOffered.class.getSimpleName()),
                    arguments(ACCEPTING, ContractNegotiationAccepting.class.getSimpleName()),
                    arguments(ACCEPTED, ContractNegotiationAccepted.class.getSimpleName()),
                    arguments(AGREEING, ContractNegotiationAgreeing.class.getSimpleName()),
                    arguments(AGREED, ContractNegotiationAgreed.class.getSimpleName()),
                    arguments(VERIFYING, ContractNegotiationVerifying.class.getSimpleName()),
                    arguments(VERIFIED, ContractNegotiationVerified.class.getSimpleName()),
                    arguments(FINALIZING, ContractNegotiationFinalizing.class.getSimpleName()),
                    arguments(TERMINATING, ContractNegotiationTerminating.class.getSimpleName())
            );
        }
    }


}
