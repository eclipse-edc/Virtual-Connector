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

package org.eclipse.edc.virtual.protocol.dsp.negotiation.http.api.v2025.controller;

import org.eclipse.edc.jsonld.spi.JsonLdNamespace;
import org.eclipse.edc.junit.annotations.ApiTest;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.protocol.dsp.negotiation.http.api.controller.DspNegotiationApiControllerTestBase;
import org.eclipse.edc.spi.result.ServiceResult;

import static org.eclipse.edc.protocol.dsp.negotiation.http.api.NegotiationApiPaths.BASE_PATH;
import static org.eclipse.edc.protocol.dsp.negotiation.http.api.NegotiationApiPaths.CONTRACT_OFFERS;
import static org.eclipse.edc.protocol.dsp.negotiation.http.api.NegotiationApiPaths.INITIAL_CONTRACT_OFFERS;
import static org.eclipse.edc.protocol.dsp.spi.type.Dsp2025Constants.DATASPACE_PROTOCOL_HTTP_V_2025_1;
import static org.eclipse.edc.protocol.dsp.spi.type.Dsp2025Constants.DSP_NAMESPACE_V_2025_1;
import static org.eclipse.edc.protocol.dsp.spi.type.Dsp2025Constants.V_2025_1_PATH;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ApiTest
class DspNegotiationApiControllerV20251Test extends DspNegotiationApiControllerTestBase {

    private final ParticipantContextService participantContextService = mock();
    private final ParticipantContext participantContext = new ParticipantContext("participantContextId");


    void beforeAll() {
        when(participantContextService.getParticipantContext(participantContext.getParticipantContextId()))
                .thenReturn(ServiceResult.success(participantContext));
    }

    @Override
    protected String basePath() {
        return "/%s".formatted(participantContext.getParticipantContextId()) + V_2025_1_PATH + BASE_PATH;
    }

    @Override
    protected JsonLdNamespace namespace() {
        return DSP_NAMESPACE_V_2025_1;
    }

    @Override
    protected Object controller() {
        return new DspNegotiationApiController20251(protocolService, participantContextService, dspRequestHandler, DATASPACE_PROTOCOL_HTTP_V_2025_1, DSP_NAMESPACE_V_2025_1);
    }

    @Override
    protected String initialOffersPath() {
        return INITIAL_CONTRACT_OFFERS;
    }

    @Override
    protected String offersPath() {
        return CONTRACT_OFFERS;
    }
}
