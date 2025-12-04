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

package org.eclipse.edc.virtualized.controlplane.transfer.subscriber.nats;

import io.nats.client.Nats;
import io.nats.client.api.StorageType;
import org.eclipse.edc.runtime.metamodel.annotation.Configuration;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.Settings;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.TransferProcessStateMachineService;

import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;
import static org.eclipse.edc.virtual.nats.NatsFunctions.createConsumer;
import static org.eclipse.edc.virtual.nats.NatsFunctions.createStream;

public class NatsTransferProcessSubscriberExtension implements ServiceExtension {

    @Configuration
    private NatsSubscriberConfig natsSubscriberConfig;

    @Inject
    private TypeManager typeManager;

    @Inject
    private TransferProcessStateMachineService stateMachineService;

    @Inject
    private Monitor monitor;

    private NatsTransferProcessSubscriber subscriber;

    @Override
    public void initialize(ServiceExtensionContext context) {
        subscriber = new NatsTransferProcessSubscriber(natsSubscriberConfig, stateMachineService, () -> typeManager.getMapper(JSON_LD), monitor);
    }

    @Override
    public void prepare() {
        if (natsSubscriberConfig.autoCreate()) {
            try (var conn = Nats.connect(natsSubscriberConfig.url())) {
                conn.jetStream();
                var jsm = conn.jetStreamManagement();
                createStream(jsm, natsSubscriberConfig.stream(), StorageType.Memory, natsSubscriberConfig.subject());
                createConsumer(jsm, natsSubscriberConfig.stream(), natsSubscriberConfig.name(), natsSubscriberConfig.subject());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void start() {
        if (subscriber != null) {
            subscriber.start();
        }
    }

    @Override
    public void shutdown() {
        if (subscriber != null) {
            subscriber.stop();
        }
    }

    @Settings
    public record NatsSubscriberConfig(

            @Setting(key = "edc.nats.tp.subscriber.url", description = "The URL of the NATS server to connect to for transfer process events.", defaultValue = "nats://localhost:4222")
            String url,
            @Setting(key = "edc.nats.tp.subscriber.name", description = "The name of the consumer for transfer process events", defaultValue = "tp-subscriber")
            String name,
            @Setting(key = "edc.nats.tp.subscriber.autocreate", description = "When true, it will automatically create the stream and the consumer if not present", defaultValue = "false")
            Boolean autoCreate,
            @Setting(key = "edc.nats.tp.subscriber.stream", description = "The stream name where to attach the consumer", defaultValue = "tp-stream")
            String stream,
            @Setting(key = "edc.nats.tp.subscriber.subject", description = "The subject of the consumer for contract negotiation events", defaultValue = "transfers.>")
            String subject
    ) {
    }
}
