/*
 *  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

plugins {
    java
}

dependencies {
    testImplementation(project(":spi:auth-spi"))
    testImplementation(libs.edc.junit)
    testImplementation(libs.edc.spi.asset)
    testImplementation(libs.edc.spi.contract)
    testImplementation(libs.edc.spi.transfer)

    // gives access to the Json LD models, etc.
    testImplementation(libs.edc.spi.jsonld)

    testImplementation(libs.restAssured)
    testImplementation(libs.awaitility)
    testImplementation(libs.nimbus.jwt)
    testImplementation(libs.wiremock)
    testImplementation(testFixtures(libs.edc.fixtures.sql))

    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(testFixtures(project(":extensions:lib:nats-lib")))
    testImplementation(project(":system-tests:system-test-fixtures"))

    testRuntimeOnly(libs.bouncyCastle.bcprovJdk18on)

}

edcBuild {
    publish.set(false)
}
