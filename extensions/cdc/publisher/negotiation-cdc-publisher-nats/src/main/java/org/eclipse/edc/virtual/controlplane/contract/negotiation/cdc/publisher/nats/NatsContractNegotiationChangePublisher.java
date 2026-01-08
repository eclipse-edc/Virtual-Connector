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
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Nats;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.virtual.controlplane.contract.negotiation.cdc.publisher.nats.ContractNegotiationCdcPublisherExtension.NatsPublisherConfig;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.ContractNegotiationChangeListener;

import java.time.Clock;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static java.lang.String.format;
import static org.eclipse.edc.spi.response.ResponseStatus.FATAL_ERROR;

public class NatsContractNegotiationChangePublisher implements ContractNegotiationChangeListener {

    public final AtomicBoolean active = new AtomicBoolean(false);
    private final NatsPublisherConfig config;
    private final Supplier<ObjectMapper> objectMapper;
    private final Clock clock;

    private JetStream js;
    private Connection connection;

    public NatsContractNegotiationChangePublisher(NatsPublisherConfig config, Supplier<ObjectMapper> objectMapper, Clock clock) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public StatusResult<Void> onChange(ContractNegotiation before, ContractNegotiation after) {
        if (!active.get()) {
            return StatusResult.failure(FATAL_ERROR, "NATS Contract Negotiation Change Listener is not active.");
        }

        var payload = new HashMap<>();
        payload.put("contractNegotiationId", after.getId());
        payload.put("state", after.stateAsString());
        payload.put("timestamp", clock.millis());

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

}
