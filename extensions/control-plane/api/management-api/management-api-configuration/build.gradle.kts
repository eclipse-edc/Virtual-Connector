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

    api(project(":spi:auth-spi"))
    implementation(libs.edc.lib.jersey.providers)
    implementation(libs.edc.lib.mgmtapi)
    implementation(libs.edc.lib.validator)
    implementation(libs.edc.lib.transform)
    implementation(libs.edc.lib.controlplane.transform)
}

edcBuild {
    swagger {
        apiGroup.set("management-api")
    }
}


