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
    testImplementation(libs.awaitility)
    testImplementation(libs.edc.spi.dataplane)
    testImplementation(libs.edc.junit)
    testImplementation(libs.restAssured)
    testImplementation(testFixtures(libs.edc.fixtures.sql))
    testImplementation(testFixtures(libs.edc.fixtures.managementapi))
    testImplementation(testFixtures((project(":system-tests:system-test-fixtures"))))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.vault)
    testImplementation(libs.wiremock)
    testImplementation(libs.nimbus.jwt)
    testImplementation(libs.bouncyCastle.bcpkixJdk18on)
}



