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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.ContractNegotiationStateMachineService;
import org.eclipse.edc.virtual.nats.testfixtures.NatsEndToEndExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;
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
        subscriber = NatsContractNegotiationSubscriber.Builder.newInstance()
                .url(NATS_EXTENSION.getNatsUrl())
                .name(CONSUMER_NAME)
                .stream(STREAM_NAME)
                .subject("negotiations.>")
                .monitor(mock())
                .mapperSupplier(ObjectMapper::new)
                .stateMachineService(stateMachineService)
                .build();

    }

    @AfterEach
    void afterEach() {
        subscriber.stop();
        NATS_EXTENSION.deleteStream(STREAM_NAME);
    }

    @ParameterizedTest
    @ArgumentsSource(StateProvider.class)
    void handleMessage(ContractNegotiationStates expectedState) {
        when(stateMachineService.handle(any(), any())).thenReturn(StatusResult.success());
        subscriber.start();
        var id = UUID.randomUUID().toString();
        var payload = """
                {
                    "state": "%s",
                    "contractNegotiationId": "%s"
                }
                """.formatted(expectedState.name(), id);

        NATS_EXTENSION.publish("negotiations.provider." + expectedState.name(), payload.getBytes());

        await().untilAsserted(() -> {
            verify(stateMachineService).handle(id, expectedState);
        });
    }


    public static class StateProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Arrays.stream(ContractNegotiationStates.values()).map(Arguments::arguments);
        }
    }


}
