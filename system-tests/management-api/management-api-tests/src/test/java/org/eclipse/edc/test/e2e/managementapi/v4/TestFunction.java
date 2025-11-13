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

package org.eclipse.edc.test.e2e.managementapi.v4;

import jakarta.json.JsonArray;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContextState;

import static jakarta.json.Json.createArrayBuilder;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2;

public class TestFunction {

    public static final String PARTICIPANT_CONTEXT_ID = "test-participant";

    public static void createParticipant(ParticipantContextService participantContextService, String participantContextId) {
        var pc = ParticipantContext.Builder.newInstance()
                .participantContextId(participantContextId)
                .state(ParticipantContextState.ACTIVATED)
                .build();

        participantContextService.createParticipantContext(pc)
                .orElseThrow(f -> new AssertionError(f.getFailureDetail()));
    }

    public static JsonArray jsonLdContext() {
        return createArrayBuilder()
                .add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2)
                .build();
    }
}
