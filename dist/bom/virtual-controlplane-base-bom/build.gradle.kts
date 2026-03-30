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
    api(project(":core:v-task-core"))
    api(project(":core:contract-negotiation-tasks"))
    api(project(":core:transfer-process-tasks"))
    api(project(":extensions:common:banner-extension"))
    runtimeOnly(libs.edc.core.dsp.virtual)
    runtimeOnly(libs.edc.mgmtapi.v5)
    runtimeOnly(libs.edc.core.mgmtapi.jsonschema)
    runtimeOnly(libs.edc.mgmtapi.authn.oauth2)
    runtimeOnly(libs.edc.mgmtapi.authz)
    runtimeOnly(libs.edc.core.connector)
    runtimeOnly(libs.edc.core.cel)
    runtimeOnly(libs.edc.core.runtime)
    runtimeOnly(libs.edc.core.token)
    runtimeOnly(libs.edc.core.jersey)
    runtimeOnly(libs.edc.core.jetty)
    runtimeOnly(libs.edc.core.policy.monitor)
    runtimeOnly(libs.edc.api.observability)
    runtimeOnly(libs.edc.core.controlplane) {
        exclude("org.eclipse.edc", "control-plane-contract-manager")
        exclude("org.eclipse.edc", "control-plane-transfer-manager")
    }
    runtimeOnly(libs.edc.core.dataplane.selector)
    runtimeOnly(libs.edc.core.dataplane.signaling.client)
    runtimeOnly(libs.edc.core.dataplane.signaling.transfer)
    runtimeOnly(libs.edc.encryption.aes)
}

