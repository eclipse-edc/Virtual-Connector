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

plugins {
    `java-library`
}

dependencies {
    api(libs.edc.mgmtapi.v5)
    api(project(":extensions:control-plane:api:management-api:catalog-api"))
    api(project(":extensions:control-plane:api:management-api:contract-definition-api"))
    api(project(":extensions:control-plane:api:management-api:policy-definition-api"))
    api(project(":extensions:control-plane:api:management-api:contract-negotiation-api"))
    api(project(":extensions:control-plane:api:management-api:contract-agreement-api"))
    api(project(":extensions:control-plane:api:management-api:transfer-process-api"))
    api(project(":extensions:control-plane:api:management-api:cel-api"))
    api(libs.edc.core.mgmtapi.jsonschema)
    api(libs.edc.mgmtapi.authn.oauth2)
    api(libs.edc.mgmtapi.authz)
}