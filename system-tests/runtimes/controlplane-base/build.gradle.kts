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
    runtimeOnly(project(":extensions:control-plane:api:management-api"))
    runtimeOnly(project(":core:v-connector-core"))
    runtimeOnly(project(":core:negotiation-manager"))
    runtimeOnly(project(":core:transfer-process-manager"))
    runtimeOnly(project(":extensions:banner-extension"))
    runtimeOnly(project(":extensions:cel:cel-extension"))
    runtimeOnly(project(":data-protocols:dsp"))
    runtimeOnly(libs.edc.core.connector)
    runtimeOnly(libs.edc.core.runtime)
    runtimeOnly(libs.edc.core.token)
    runtimeOnly(libs.edc.core.jersey)
    runtimeOnly(libs.edc.core.jetty)
    runtimeOnly(libs.edc.api.observability)
    runtimeOnly(libs.bundles.dcp)
    runtimeOnly(libs.edc.core.controlplane) {
        exclude("org.eclipse.edc", "control-plane-contract-manager")
        exclude("org.eclipse.edc", "control-plane-transfer-manager")
    }
    runtimeOnly(libs.edc.core.dataplane.selector)
    runtimeOnly(libs.edc.core.dataplane.signaling.client)
    runtimeOnly(libs.edc.core.dataplane.signaling.transfer)
}

edcBuild {
    publish.set(false)
}


