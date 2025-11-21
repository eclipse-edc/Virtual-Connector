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

package org.eclipse.edc.virtualized.controlplane.participantcontext;

import org.eclipse.edc.participantcontext.spi.identity.ParticipantIdentityResolver;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.monitor.Monitor;
import org.jetbrains.annotations.Nullable;

public class ParticipantContextIdentityResolverImpl implements ParticipantIdentityResolver {

    private final ParticipantContextService participantContextService;
    private final Monitor monitor;

    public ParticipantContextIdentityResolverImpl(ParticipantContextService participantContextService, Monitor monitor) {
        this.participantContextService = participantContextService;
        this.monitor = monitor;
    }

    @Override
    public @Nullable String getParticipantId(String participantContextId, String protocol) {
        return participantContextService.getParticipantContext(participantContextId)
                .map(ParticipantContext::getIdentity)
                .getContent();

    }
}
