/*
 *  Copyright (c) 2026 Metaform Systems, Inc.
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
    api(libs.edc.spi.transfer)
    api(project(":spi:v-core-spi"))
    api(project(":spi:v-task-spi"))
    implementation(libs.nats.client)
    implementation(project(":extensions:lib:nats-lib"))
    testImplementation(libs.awaitility)
    testImplementation(libs.edc.junit)
    testImplementation(testFixtures(libs.edc.spi.contract))
    testImplementation(libs.testcontainers.junit)
    testImplementation(testFixtures(project(":extensions:lib:nats-lib")))
    testImplementation(project(":core:v-task-core"))
}

