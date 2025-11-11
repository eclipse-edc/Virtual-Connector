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

package org.eclipse.edc.virtualized.connector.controlplane.api.management.asset.v4;

import org.eclipse.edc.virtualized.connector.controlplane.api.management.asset.AssetApiControllerTest;

public class AssetApiV4ControllerTest extends AssetApiControllerTest {
    @Override
    protected String versionPath() {
        return "v4alpha";
    }

    @Override
    protected Object controller() {
        return new AssetApiV4Controller(assetService, transformerRegistry, validator, monitor, authorizationService);
    }
}
