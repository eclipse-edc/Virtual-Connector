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

package org.eclipse.edc.virtual.controlplane;

import org.eclipse.edc.participantcontext.spi.identity.ParticipantIdentityResolver;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.virtual.controlplane.participantcontext.ParticipantContextIdentityResolverImpl;
import org.eclipse.edc.virtual.controlplane.participantcontext.ParticipantWebhookResolverImpl;
import org.eclipse.edc.virtual.controlplane.participantcontext.spi.ParticipantWebhookResolver;

import static org.eclipse.edc.virtual.controlplane.VirtualCoreServicesExtension.NAME;

@Extension(NAME)
public class VirtualCoreServicesExtension implements ServiceExtension {

    public static final String NAME = "EDC-V Core Services";

    @Inject
    private ParticipantContextService participantContextService;

    @Inject
    private DataspaceProfileContextRegistry dataspaceProfileContextRegistry;

    @Inject
    private Monitor monitor;

    @Provider
    public ParticipantWebhookResolver participantWebhookResolver() {
        return new ParticipantWebhookResolverImpl(dataspaceProfileContextRegistry);
    }

    @Provider
    public ParticipantIdentityResolver participantIdentityResolver() {
        return new ParticipantContextIdentityResolverImpl(participantContextService, monitor);
    }
}
