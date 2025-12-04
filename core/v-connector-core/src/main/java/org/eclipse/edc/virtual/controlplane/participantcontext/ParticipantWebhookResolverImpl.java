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

package org.eclipse.edc.virtual.controlplane.participantcontext;

import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.protocol.spi.ProtocolWebhook;
import org.eclipse.edc.virtual.controlplane.participantcontext.spi.ParticipantWebhookResolver;

import java.util.Optional;

public class ParticipantWebhookResolverImpl implements ParticipantWebhookResolver {

    private final DataspaceProfileContextRegistry registry;

    public ParticipantWebhookResolverImpl(DataspaceProfileContextRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ProtocolWebhook getWebhook(String participantContextId, String protocol) {
        return Optional.ofNullable(registry.getWebhook(protocol))
                .map(protocolWebhook -> wrap(participantContextId, protocolWebhook))
                .orElse(null);
    }

    private ProtocolWebhook wrap(String participantContextId, ProtocolWebhook protocolWebhook) {
        return () -> protocolWebhook.url().formatted(participantContextId);

    }

}
