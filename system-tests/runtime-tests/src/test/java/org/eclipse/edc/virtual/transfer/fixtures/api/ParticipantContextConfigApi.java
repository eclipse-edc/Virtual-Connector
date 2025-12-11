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
import org.eclipse.edc.virtual.transfer.fixtures.api.model.ParticipantContextConfigDto;
import org.eclipse.edc.virtual.transfer.fixtures.api.model.WithContext;

import static io.restassured.http.ContentType.JSON;

/**
 * API client for participant context configuration-related operations.
 */
public class ParticipantContextConfigApi {
    private final VirtualConnectorClient connector;

    public ParticipantContextConfigApi(VirtualConnectorClient connector) {
        this.connector = connector;
    }

    /**
     * Saves the configuration for a participant context.
     *
     * @param participantContextId  the participant context ID
     * @param participantContextDto the participant context configuration to save
     */
    public void saveConfig(String participantContextId, ParticipantContextConfigDto participantContextDto) {
        connector.baseManagementRequest(null, ParticipantPrincipal.ROLE_PROVISIONER)
                .contentType(JSON)
                .body(new WithContext<>(participantContextDto))
                .when()
                .put("/v4alpha/participants/%s/config".formatted(participantContextId))
                .then()
                .log().ifError()
                .statusCode(204);
    }


}
