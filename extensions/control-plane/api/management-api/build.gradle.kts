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
    api(project(":extensions:common:api:api-authentication"))
    api(project(":extensions:common:api:api-authorization"))
    api(project(":extensions:control-plane:api:management-api:asset-api"))
    api(project(":extensions:control-plane:api:management-api:catalog-api"))
    api(project(":extensions:control-plane:api:management-api:contract-definition-api"))
    api(project(":extensions:control-plane:api:management-api:policy-definition-api"))
    api(project(":extensions:control-plane:api:management-api:contract-negotiation-api"))
    api(project(":extensions:control-plane:api:management-api:contract-agreement-api"))
    api(project(":extensions:control-plane:api:management-api:transfer-process-api"))
    api(project(":extensions:control-plane:api:management-api:participant-context-api"))
    api(project(":extensions:control-plane:api:management-api:participant-context-config-api"))
    api(project(":extensions:control-plane:api:management-api:cel-api"))
    api(project(":extensions:control-plane:api:management-api:management-api-configuration"))
}