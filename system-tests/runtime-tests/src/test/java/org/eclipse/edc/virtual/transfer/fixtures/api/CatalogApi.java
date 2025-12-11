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

import org.eclipse.edc.virtual.transfer.fixtures.VirtualConnectorClient;
import org.eclipse.edc.virtual.transfer.fixtures.api.model.DatasetDto;
import org.eclipse.edc.virtual.transfer.fixtures.api.model.DatasetRequestDto;
import org.eclipse.edc.virtual.transfer.fixtures.api.model.WithContext;

import static io.restassured.http.ContentType.JSON;

/**
 * API client for catalog-related operations.
 */
public class CatalogApi {
    private final VirtualConnectorClient connector;

    public CatalogApi(VirtualConnectorClient connector) {
        this.connector = connector;
    }

    /**
     * Requests a dataset from the catalog in the specified participant context.
     *
     * @param participantContextId the participant context ID
     * @param datasetRequest       the dataset request
     * @return the requested dataset
     */
    public DatasetDto getDataset(String participantContextId, DatasetRequestDto datasetRequest) {
        return connector.baseManagementRequest(participantContextId)
                .contentType(JSON)
                .body(new WithContext<>(datasetRequest))
                .when()
                .post("/v4alpha/participants/%s/catalog/dataset/request".formatted(participantContextId))
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .contentType(JSON)
                .extract().as(DatasetDto.class);
    }


}
