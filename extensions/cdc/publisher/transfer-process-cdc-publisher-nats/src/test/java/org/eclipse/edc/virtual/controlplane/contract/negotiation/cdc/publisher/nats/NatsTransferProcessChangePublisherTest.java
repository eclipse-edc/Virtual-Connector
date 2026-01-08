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

package org.eclipse.edc.virtual.controlplane.contract.negotiation.cdc.publisher.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.JetStreamApiException;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.virtual.controlplane.contract.transfer.cdc.publisher.nats.NatsTransferProcessChangePublisher;
import org.eclipse.edc.virtual.controlplane.contract.transfer.cdc.publisher.nats.TransferProcessCdcPublisherExtension.NatsPublisherConfig;
import org.eclipse.edc.virtual.nats.testfixtures.NatsEndToEndExtension;
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
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;


@ComponentTest
@Testcontainers
public class NatsTransferProcessChangePublisherTest {

    public static final String STREAM_NAME = "stream_test";
    public static final String CONSUMER_NAME = "consumer_test";

    @Order(0)
    @RegisterExtension
    static final NatsEndToEndExtension NATS_EXTENSION = new NatsEndToEndExtension();

    private final ObjectMapper mapper = new ObjectMapper();
    private NatsTransferProcessChangePublisher publisher;


    @BeforeEach
    void beforeEach() throws JetStreamApiException, IOException {
        NATS_EXTENSION.createStream(STREAM_NAME, "transfers.>");
        NATS_EXTENSION.createConsumer(STREAM_NAME, CONSUMER_NAME);

        var config = new NatsPublisherConfig(NATS_EXTENSION.getNatsUrl(), "transfers");
        publisher = new NatsTransferProcessChangePublisher(config, () -> mapper, Clock.systemUTC()
        );
    }

    @AfterEach
    void afterEach() {
        NATS_EXTENSION.deleteStream(STREAM_NAME);
    }

    @ParameterizedTest
    @ArgumentsSource(TransferProcessProvider.class)
    void onChange(TransferProcess transferProcess) throws Exception {

        publisher.start();

        var result = publisher.onChange(null, transferProcess);

        assertThat(result).isSucceeded();

        var payload = NATS_EXTENSION.nextMessage(STREAM_NAME, CONSUMER_NAME);
        assertThat(payload.get("state")).isEqualTo(transferProcess.stateAsString());
        assertThat(payload.get("transferProcessId")).isEqualTo(transferProcess.getId());
    }

    @Test
    void onChange_NotActive() {
        var result = publisher.onChange(null, null);
        assertThat(result).isFailed();

        var cn = TransferProcess.Builder.newInstance()
                .id("cn-1")
                .counterPartyAddress("http://example.com")
                .protocol("test-protocol")
                .build();

        publisher.start();
        result = publisher.onChange(null, cn);
        assertThat(result).isSucceeded();

        publisher.stop();
        result = publisher.onChange(null, cn);
        assertThat(result).isFailed();
    }

    public static class TransferProcessProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {

            var cnBuilder = TransferProcess.Builder.newInstance()
                    .id("cn-1")
                    .counterPartyAddress("http://example.com")
                    .protocol("test-protocol");

            return Arrays.stream(TransferProcessStates.values()).map(s -> arguments(cnBuilder.state(s.code()).build().copy()));
        }
    }
}
