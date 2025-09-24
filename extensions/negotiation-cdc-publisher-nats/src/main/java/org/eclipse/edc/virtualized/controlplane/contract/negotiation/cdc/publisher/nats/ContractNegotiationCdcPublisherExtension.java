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

import org.eclipse.edc.runtime.metamodel.annotation.Configuration;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.Settings;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.ContractNegotiationChangeListener;

import java.time.Clock;

import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

public class ContractNegotiationCdcPublisherExtension implements ServiceExtension {

    @Inject
    private TypeManager typeManager;

    @Configuration
    private NatsPublisherConfig natsPublisherConfig;

    @Inject
    private Clock clock;

    private NatsContractNegotiationChangePublisher changePublisher;

    @Provider
    public ContractNegotiationChangeListener changeListener() {

        if (changePublisher == null) {
            changePublisher = new NatsContractNegotiationChangePublisher(natsPublisherConfig, () -> typeManager.getMapper(JSON_LD), clock);
        }
        return changePublisher;
    }

    @Override
    public void start() {
        if (changePublisher != null) {
            changePublisher.start();
        }
    }

    @Override
    public void shutdown() {
        if (changePublisher != null) {
            changePublisher.stop();
        }
    }

    @Settings
    public record NatsPublisherConfig(
            @Setting(key = "edc.nats.cn.publisher.url", description = "The URL of the NATS server to connect to for publishing contract negotiation events.", defaultValue = "nats://localhost:4222")
            String url,
            @Setting(key = "edc.nats.cn.publisher.subject-prefix", description = "The prefix for the subjects", defaultValue = "negotiations")
            String subjectPrefix
    ) {
    }
}
