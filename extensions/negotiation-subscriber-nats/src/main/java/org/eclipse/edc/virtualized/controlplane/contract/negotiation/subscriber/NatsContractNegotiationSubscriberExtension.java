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

import org.eclipse.edc.runtime.metamodel.annotation.Configuration;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.Settings;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.ContractNegotiationStateMachineService;

import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

public class NatsContractNegotiationSubscriberExtension implements ServiceExtension {


    @Configuration
    private NatsSubscriberConfig natsSubscriberConfig;

    @Inject
    private TypeManager typeManager;

    @Inject
    private ContractNegotiationStateMachineService stateMachineService;

    @Inject
    private Monitor monitor;

    private NatsContractNegotiationSubscriber subscriber;

    @Override
    public void initialize(ServiceExtensionContext context) {
        subscriber = new NatsContractNegotiationSubscriber(natsSubscriberConfig, stateMachineService, () -> typeManager.getMapper(JSON_LD), monitor);
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
            @Setting(key = "edc.nats.cn.subscriber.url", description = "The URL of the NATS server to connect to for contract negotiation events.", defaultValue = "nats://localhost:4222")
            String url,
            @Setting(key = "edc.nats.cn.subscriber.name", description = "The name of the consumer for contract negotiation events", defaultValue = "cn-subscriber")
            String name,
            @Setting(key = "edc.nats.cn.subscriber.subject", description = "The subject of the consumer for contract negotiation events", defaultValue = "negotiations.>")
            String subject
    ) {
    }
}
