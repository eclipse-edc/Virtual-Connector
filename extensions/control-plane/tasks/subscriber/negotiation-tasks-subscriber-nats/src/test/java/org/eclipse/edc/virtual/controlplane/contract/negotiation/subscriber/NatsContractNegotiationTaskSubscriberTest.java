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

package org.eclipse.edc.virtual.controlplane.contract.negotiation.subscriber;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.spi.response.StatusResult;
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
import org.eclipse.edc.virtual.controlplane.tasks.ProcessTaskPayload;
import org.eclipse.edc.virtual.controlplane.tasks.Task;
import org.eclipse.edc.virtual.controlplane.tasks.TaskTypes;
import org.eclipse.edc.virtual.nats.testfixtures.NatsEndToEndExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation.Type.CONSUMER;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation.Type.PROVIDER;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.AGREED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.AGREEING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.FINALIZED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.FINALIZING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.INITIAL;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.REQUESTED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.REQUESTING;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.VERIFIED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.VERIFYING;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NatsContractNegotiationTaskSubscriberTest {

    public static final String STREAM_NAME = "stream_test";
    public static final String CONSUMER_NAME = "consumer_test";
    @Order(0)
    @RegisterExtension
    static final NatsEndToEndExtension NATS_EXTENSION = new NatsEndToEndExtension();
    private final ContractNegotiationTaskExecutor taskManager = mock();
    private NatsContractNegotiationTaskSubscriber subscriber;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void beforeAll() {
        TaskTypes.TYPES.forEach(MAPPER::registerSubtypes);
    }

    @BeforeEach
    void beforeEach() {
        NATS_EXTENSION.createStream(STREAM_NAME, "negotiations.>");
        NATS_EXTENSION.createConsumer(STREAM_NAME, CONSUMER_NAME, "negotiations.>");
        subscriber = NatsContractNegotiationTaskSubscriber.Builder.newInstance()
                .url(NATS_EXTENSION.getNatsUrl())
                .name(CONSUMER_NAME)
                .stream(STREAM_NAME)
                .subject("negotiations.>")
                .monitor(mock())
                .mapperSupplier(() -> MAPPER)
                .taskExecutor(taskManager)
                .build();

    }

    @AfterEach
    void afterEach() {
        subscriber.stop();
        NATS_EXTENSION.deleteStream(STREAM_NAME);
    }

    @ParameterizedTest
    @ArgumentsSource(StateTransitionProvider.class)
    void handleMessage(ContractNegotiationTaskPayload payload) throws JsonProcessingException {
        when(taskManager.handle(any())).thenReturn(StatusResult.success());
        subscriber.start();
        var task = Task.Builder.newInstance().at(System.currentTimeMillis())
                .payload(payload)
                .build();

        NATS_EXTENSION.publish("negotiations.provider." + payload.name(), MAPPER.writeValueAsBytes(task));

        await().untilAsserted(() -> {
            verify(taskManager).handle(refEq(payload));
        });
    }


    public static class StateTransitionProvider implements ArgumentsProvider {

        protected <T extends ProcessTaskPayload, B extends ProcessTaskPayload.Builder<T, B>> B baseBuilder(B builder, String id, ContractNegotiationStates state, ContractNegotiation.Type type) {
            return builder.processId(id)
                    .processState(state.name())
                    .processType(type.name());
        }

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {

            return Stream.of(
                    arguments(baseBuilder(RequestNegotiation.Builder.newInstance(), "id", INITIAL, CONSUMER).build(), REQUESTING),
                    arguments(baseBuilder(SendRequestNegotiation.Builder.newInstance(), "id", REQUESTING, CONSUMER).build(), REQUESTED),
                    arguments(baseBuilder(VerifyNegotiation.Builder.newInstance(), "id", AGREED, CONSUMER).build(), VERIFYING),
                    arguments(baseBuilder(SendVerificationNegotiation.Builder.newInstance(), "id", VERIFYING, CONSUMER).build(), VERIFIED),
                    arguments(baseBuilder(AgreeNegotiation.Builder.newInstance(), "id", REQUESTED, PROVIDER).build(), AGREEING),
                    arguments(baseBuilder(SendAgreement.Builder.newInstance(), "id", AGREEING, PROVIDER).build(), AGREED),
                    arguments(baseBuilder(FinalizeNegotiation.Builder.newInstance(), "id", VERIFIED, PROVIDER).build(), FINALIZING),
                    arguments(baseBuilder(SendFinalizeNegotiation.Builder.newInstance(), "id", FINALIZING, PROVIDER).build(), FINALIZED)
            );
        }
    }


}
