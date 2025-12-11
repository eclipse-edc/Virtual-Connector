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

package org.eclipse.edc.virtual.transfer.fixtures.api;

import org.eclipse.edc.api.auth.spi.ParticipantPrincipal;
import org.eclipse.edc.virtual.transfer.fixtures.VirtualConnectorClient;
import org.eclipse.edc.virtual.transfer.fixtures.api.model.ParticipantContextDto;
import org.eclipse.edc.virtual.transfer.fixtures.api.model.WithContext;

import static io.restassured.http.ContentType.JSON;

public class ParticipantContextApi {
    private final VirtualConnectorClient connector;

    public ParticipantContextApi(VirtualConnectorClient connector) {
        this.connector = connector;
    }

    /**
     * Creates a participant context.
     *
     * @param participantContextDto the participant context to create
     */
    public void createParticipant(ParticipantContextDto participantContextDto) {
        connector.baseManagementRequest(null, ParticipantPrincipal.ROLE_PROVISIONER)
                .contentType(JSON)
                .body(new WithContext<>(participantContextDto))
                .when()
                .post("/v4alpha/participants")
                .then()
                .log().ifError()
                .statusCode(200);
    }


}
