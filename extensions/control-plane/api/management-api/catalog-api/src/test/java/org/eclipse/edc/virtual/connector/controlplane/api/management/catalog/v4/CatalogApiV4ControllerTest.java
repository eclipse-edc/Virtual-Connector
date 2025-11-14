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

package org.eclipse.edc.virtual.connector.controlplane.api.management.catalog.v4;


import org.eclipse.edc.junit.annotations.ApiTest;
import org.eclipse.edc.virtual.connector.controlplane.api.management.catalog.BaseCatalogApiControllerTest;

@ApiTest
class CatalogApiV4ControllerTest extends BaseCatalogApiControllerTest {

    @Override
    protected String baseUrl(String participantContextId) {
        return "/v4alpha/participants/%s/catalog".formatted(participantContextId);
    }

    @Override
    protected Object controller() {
        return new CatalogApiV4Controller(service, transformerRegistry, authorizationService, participantContextService);
    }
}