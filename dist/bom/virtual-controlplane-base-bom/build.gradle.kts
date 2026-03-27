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
    api(project(":extensions:control-plane:api:management-api"))
    api(project(":core:v-task-core"))
    api(project(":core:contract-negotiation-tasks"))
    api(project(":core:transfer-process-tasks"))
    api(project(":extensions:common:banner-extension"))
    runtimeOnly(libs.edc.core.dsp.virtual)
    runtimeOnly(libs.edc.lib.oauth2.authn)
    runtimeOnly(libs.edc.lib.oauth2.authz)
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

