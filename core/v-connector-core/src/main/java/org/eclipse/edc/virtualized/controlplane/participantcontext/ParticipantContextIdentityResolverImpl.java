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

import org.eclipse.edc.participantcontext.spi.config.ParticipantContextConfig;
import org.eclipse.edc.participantcontext.spi.identity.ParticipantIdentityResolver;
import org.jetbrains.annotations.Nullable;

import static org.eclipse.edc.virtualized.controlplane.VirtualCoreServicesExtension.PARTICIPANT_ID;

public class ParticipantContextIdentityResolverImpl implements ParticipantIdentityResolver {

    private final ParticipantContextConfig contextConfig;

    public ParticipantContextIdentityResolverImpl(ParticipantContextConfig contextConfig) {
        this.contextConfig = contextConfig;
    }

    @Override
    public @Nullable String getParticipantId(String participantContextId, String protocol) {
        return contextConfig.getString(participantContextId, PARTICIPANT_ID);
    }
}
