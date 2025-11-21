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

package org.eclipse.edc.virtua.tck.dsp;

import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.ServiceExtension;

@Extension(DspTckExtension.NAME)
public class DspTckExtension implements ServiceExtension {

    public static final String NAME = "DSP TCK Extension";

    @Inject
    private ParticipantContextService participantContextService;

    @Override
    public void prepare() {
        participantContextService.createParticipantContext(ParticipantContext.Builder.newInstance()
                        .participantContextId("participantContextId")
                        .identity("participantContextId")
                        .build())
                .orElseThrow(f -> new EdcException("Failed to create ParticipantContext"));
    }
}
