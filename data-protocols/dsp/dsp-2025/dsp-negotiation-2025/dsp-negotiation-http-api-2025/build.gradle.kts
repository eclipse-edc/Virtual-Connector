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
    api(libs.edc.spi.jsonld)
    api(libs.edc.spi.web)
    api(libs.edc.spi.dsp)
    api(libs.edc.spi.dsp.v2025)
    api(libs.edc.spi.dsp.http)

    implementation(libs.edc.lib.jersey.providers)
    implementation(libs.edc.lib.dsp.negotiation.validation)
    implementation(libs.edc.lib.dsp.negotiation.http)

    testImplementation(testFixtures(libs.edc.core.jersey))

    testImplementation(testFixtures(libs.edc.lib.dsp.negotiation.http));

}