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
    id(libs.plugins.swagger.get().pluginId)

}

dependencies {

    api(project(":spi:v-auth-spi"))
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.controlplane)
    implementation(libs.edc.spi.web)
    implementation(libs.edc.spi.transform)
    implementation(libs.edc.lib.controlplane.transform)
    implementation(libs.jakarta.annotation)

    implementation(libs.edc.lib.api)
    implementation(libs.edc.lib.jersey.providers)
    implementation(libs.edc.lib.mgmtapi)
    implementation(libs.edc.lib.validator)

    testImplementation(testFixtures(libs.edc.core.jersey))
    testImplementation(libs.edc.lib.transform)
    testImplementation(libs.restAssured)
    testImplementation(libs.awaitility)
}

edcBuild {
    swagger {
        apiGroup.set("management-api")
    }
}