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

package org.eclipse.edc.virtualized.controlplane;

import org.eclipse.edc.participantcontext.spi.config.ParticipantContextConfig;
import org.eclipse.edc.participantcontext.spi.identity.ParticipantIdentityResolver;
import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.virtualized.controlplane.participantcontext.ParticipantContextIdentityResolverImpl;
import org.eclipse.edc.virtualized.controlplane.participantcontext.ParticipantWebhookResolverImpl;
import org.eclipse.edc.virtualized.controlplane.participantcontext.spi.ParticipantWebhookResolver;

import static org.eclipse.edc.virtualized.controlplane.VirtualCoreServicesExtension.NAME;

@Extension(NAME)
public class VirtualCoreServicesExtension implements ServiceExtension {

    public static final String NAME = "EDC-V Core Services";

    @Setting(description = "Configures the participant id this runtime is operating on behalf of")
    public static final String PARTICIPANT_ID = "edc.participant.id";

    @Inject
    private ParticipantContextConfig contextConfig;

    @Inject
    private DataspaceProfileContextRegistry dataspaceProfileContextRegistry;

    @Provider
    public ParticipantWebhookResolver participantWebhookResolver() {
        return new ParticipantWebhookResolverImpl(dataspaceProfileContextRegistry);
    }

    @Provider
    public ParticipantIdentityResolver participantIdentityResolver() {
        return new ParticipantContextIdentityResolverImpl(contextConfig);
    }
}
