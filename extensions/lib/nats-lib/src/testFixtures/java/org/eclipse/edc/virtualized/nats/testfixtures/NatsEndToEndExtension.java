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

package org.eclipse.edc.virtualized.nats.testfixtures;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.util.Map;

/**
 * Extension to be used in end-to-end tests with Nats
 */
public class NatsEndToEndExtension implements BeforeAllCallback, AfterAllCallback {

    private static final String DEFAULT_IMAGE = "nats:2.9.22";
    private static JetStreamManagement jsm;
    private static JetStream js;
    private final ObjectMapper mapper = new ObjectMapper();
    private final GenericContainer<?> nats;

    public NatsEndToEndExtension() {
        this(DEFAULT_IMAGE);
    }

    @SuppressWarnings("resource")
    public NatsEndToEndExtension(String dockerImageName) {
        this(new GenericContainer<>(dockerImageName)
                .withExposedPorts(4222)
                .withCommand("-js"));
    }

    public NatsEndToEndExtension(GenericContainer<?> container) {
        nats = container;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws IOException, InterruptedException {
        nats.start();
        var conn = Nats.connect(getNatsUrl());
        jsm = conn.jetStreamManagement();
        js = conn.jetStream();
    }

    
    public void createStream(String streamName, String... subject) {
        var streamConfig = StreamConfiguration.builder()
                .name(streamName)
                .subjects(subject)
                .storageType(StorageType.Memory)
                .build();
        try {
            jsm.addStream(streamConfig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteStream(String streamName) {
        try {
            jsm.deleteStream(streamName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void createConsumer(String streamName, String consumerName) {
        createConsumer(streamName, consumerName, null);
    }

    public void createConsumer(String streamName, String consumerName, String filterSubject) {
        try {
            jsm.addOrUpdateConsumer(streamName, io.nats.client.api.ConsumerConfiguration.builder()
                    .durable(consumerName)
                    .name(consumerName)
                    .filterSubject(filterSubject)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void publish(String subject, byte[] message) {
        try {
            js.publish(subject, message);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void publish(String subject, Object payload) {
        try {
            var message = mapper.writeValueAsString(payload);
            publish(subject, message.getBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> nextMessage(String streamName, String consumerName) {
        try {
            var ctx = js.getConsumerContext(streamName, consumerName);
            try (var fetcher = ctx.fetchMessages(1)) {
                var msg = fetcher.nextMessage();
                return mapper.readValue(msg.getData(), new TypeReference<>() {
                });
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getNatsUrl() {
        return "nats://" + nats.getHost() + ":" + nats.getMappedPort(4222);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        nats.stop();
        nats.close();
    }

}
