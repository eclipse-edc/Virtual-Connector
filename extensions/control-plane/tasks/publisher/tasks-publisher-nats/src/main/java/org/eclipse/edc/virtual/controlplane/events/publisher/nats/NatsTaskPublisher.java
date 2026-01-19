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

package org.eclipse.edc.virtual.controlplane.events.publisher.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.JetStream;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.ContractNegotiationTaskPayload;
import org.eclipse.edc.virtual.controlplane.tasks.Task;
import org.eclipse.edc.virtual.controlplane.tasks.TaskListener;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.TransferProcessTaskPayload;

import java.util.function.Supplier;

import static java.lang.String.format;

public class NatsTaskPublisher implements TaskListener {

    private final JetStream js;
    private final Monitor monitor;
    private final Supplier<ObjectMapper> objectMapper;

    public NatsTaskPublisher(JetStream js, Monitor monitor, Supplier<ObjectMapper> objectMapper) {
        this.js = js;
        this.monitor = monitor;
        this.objectMapper = objectMapper;
    }


    private String formatSubject(Task task) {
        if ((task.getPayload() instanceof ContractNegotiationTaskPayload t)) {
            return format("negotiations.%s.%s", t.getProcessType().toLowerCase(), t.name());
        }
        if ((task.getPayload() instanceof TransferProcessTaskPayload t)) {
            return format("transfers.%s.%s", t.getProcessType().toLowerCase(), t.name());
        }
        monitor.severe("Unsupported event type for task id " + task.getId());
        throw new IllegalArgumentException("Unsupported event type: " + task.getPayload());
    }

    @Override
    public void created(Task task) {
        try {
            var message = objectMapper.get().writeValueAsString(task);
            js.publish(formatSubject(task), message.getBytes());
        } catch (Exception e) {
            monitor.severe("Failed to publish task created event for task id " + task.getId(), e);
            throw new EdcException(e);
        }

    }
}
