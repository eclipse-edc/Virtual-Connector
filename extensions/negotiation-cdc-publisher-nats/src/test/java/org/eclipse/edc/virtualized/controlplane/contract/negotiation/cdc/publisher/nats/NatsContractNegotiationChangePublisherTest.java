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
import io.nats.client.JetStreamApiException;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationAccepted;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationAgreed;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationInitiated;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationOffered;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationRequested;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationVerified;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.virtualized.controlplane.contract.negotiation.cdc.publisher.nats.ContractNegotiationCdcPublisherExtension.NatsPublisherConfig;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Clock;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
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
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;


@ComponentTest
@Testcontainers
public class NatsContractNegotiationChangePublisherTest {

    public static final String STREAM_NAME = "stream_test";
    public static final String CONSUMER_NAME = "consumer_test";

    @Order(0)
    @RegisterExtension
    static final NatsEndToEndExtension NATS_EXTENSION = new NatsEndToEndExtension();

    private final ObjectMapper mapper = new ObjectMapper();
    private NatsContractNegotiationChangePublisher publisher;


    @BeforeEach
    void beforeEach() throws JetStreamApiException, IOException {
        NATS_EXTENSION.createStream(STREAM_NAME, "negotiations.>");
        NATS_EXTENSION.createConsumer(STREAM_NAME, CONSUMER_NAME);

        var config = new NatsPublisherConfig(NATS_EXTENSION.getNatsUrl(), "negotiations");
        publisher = new NatsContractNegotiationChangePublisher(config, () -> mapper, Clock.systemUTC()
        );
    }

    @AfterEach
    void afterEach() {
        NATS_EXTENSION.deleteStream(STREAM_NAME);
    }

    @ParameterizedTest
    @ArgumentsSource(ContractNegotiationProvider.class)
    void onChange(ContractNegotiation cn, String expectedType) throws Exception {

        publisher.start();

        var result = publisher.onChange(null, cn);

        assertThat(result).isSucceeded();

        var payload = NATS_EXTENSION.nextMessage(STREAM_NAME, CONSUMER_NAME);
        assertThat(payload.get("type")).isEqualTo(expectedType);
    }

    @Test
    void onChange_NotActive() {
        var result = publisher.onChange(null, null);
        assertThat(result).isFailed();

        var cn = ContractNegotiation.Builder.newInstance()
                .id("cn-1")
                .counterPartyAddress("http://example.com")
                .counterPartyId("counterparty-1")
                .protocol("test-protocol")
                .build();

        publisher.start();
        result = publisher.onChange(null, cn);
        assertThat(result).isSucceeded();

        publisher.stop();
        result = publisher.onChange(null, cn);
        assertThat(result).isFailed();
    }

    public static class ContractNegotiationProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {

            var cnBuilder = ContractNegotiation.Builder.newInstance()
                    .id("cn-1")
                    .counterPartyAddress("http://example.com")
                    .counterPartyId("counterparty-1")
                    .protocol("test-protocol");

            return Stream.of(
                    arguments(cnBuilder.state(INITIAL.code()).build().copy(), ContractNegotiationInitiated.class.getSimpleName()),
                    arguments(cnBuilder.state(REQUESTING.code()).build().copy(), ContractNegotiationRequesting.class.getSimpleName()),
                    arguments(cnBuilder.state(REQUESTED.code()).build().copy(), ContractNegotiationRequested.class.getSimpleName()),
                    arguments(cnBuilder.state(OFFERING.code()).build().copy(), ContractNegotiationOffering.class.getSimpleName()),
                    arguments(cnBuilder.state(OFFERED.code()).build().copy(), ContractNegotiationOffered.class.getSimpleName()),
                    arguments(cnBuilder.state(ACCEPTING.code()).build().copy(), ContractNegotiationAccepting.class.getSimpleName()),
                    arguments(cnBuilder.state(ACCEPTED.code()).build().copy(), ContractNegotiationAccepted.class.getSimpleName()),
                    arguments(cnBuilder.state(AGREEING.code()).build().copy(), ContractNegotiationAgreeing.class.getSimpleName()),
                    arguments(cnBuilder.state(AGREED.code()).build().copy(), ContractNegotiationAgreed.class.getSimpleName()),
                    arguments(cnBuilder.state(VERIFYING.code()).build().copy(), ContractNegotiationVerifying.class.getSimpleName()),
                    arguments(cnBuilder.state(VERIFIED.code()).build().copy(), ContractNegotiationVerified.class.getSimpleName()),
                    arguments(cnBuilder.state(FINALIZING.code()).build().copy(), ContractNegotiationFinalizing.class.getSimpleName()),
                    arguments(cnBuilder.state(TERMINATING.code()).build().copy(), ContractNegotiationTerminating.class.getSimpleName())
            );
        }
    }
}
