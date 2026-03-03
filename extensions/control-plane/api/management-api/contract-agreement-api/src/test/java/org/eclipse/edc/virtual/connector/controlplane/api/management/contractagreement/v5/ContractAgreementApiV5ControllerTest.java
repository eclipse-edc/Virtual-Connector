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

package org.eclipse.edc.virtual.connector.controlplane.api.management.contractagreement.v5;

import org.eclipse.edc.virtual.connector.controlplane.api.management.contractagreement.BaseContractAgreementApiControllerTest;

class ContractAgreementApiV5ControllerTest extends BaseContractAgreementApiControllerTest {
    @Override
    protected Object controller() {
        return new ContractAgreementApiV5Controller(service, authorizationService, transformerRegistry, monitor, validatorRegistry);
    }

    @Override
    protected String versionPath() {
        return "v5alpha";
    }
}