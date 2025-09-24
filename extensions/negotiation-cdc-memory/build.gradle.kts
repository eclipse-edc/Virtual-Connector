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
    api(libs.edc.spi.core)
    api(libs.edc.spi.contract)
    api(project(":spi:v-core-spi"))
    implementation(libs.edc.core.controlplane) {
        exclude("org.eclipse.edc", "control-plane-contract-manager")
    }
    implementation(libs.edc.lib.store)
    testImplementation(libs.awaitility)
    testImplementation(libs.edc.junit)
    testImplementation(libs.edc.lib.query)
    testImplementation(testFixtures(libs.edc.spi.contract))

}

