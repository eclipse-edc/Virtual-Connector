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
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.participant)
    implementation(libs.edc.spi.contract)
    implementation(libs.edc.spi.verifiablecredentials)
    implementation(libs.edc.spi.catalog)
    implementation(libs.edc.spi.policy.engine)
    implementation(libs.edc.spi.policy.monitor)
    implementation(libs.edc.spi.transaction)
    implementation(libs.edc.lib.store)
    implementation(project(":extensions:cel:cel-spi"))
    implementation(libs.cel)

    testImplementation(libs.edc.junit)
    testImplementation(testFixtures(project(":extensions:cel:cel-spi")))
    testImplementation(libs.edc.lib.query)

}

