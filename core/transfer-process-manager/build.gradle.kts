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
    api(project(":spi:v-core-spi"))
    api(libs.edc.spi.core)
    api(libs.edc.spi.transaction)
    api(libs.edc.spi.protocol)
    api(libs.edc.spi.transfer)
    api(libs.edc.spi.policy)
    implementation(libs.opentelemetry.instrumentation.annotations)
    testImplementation(libs.edc.junit)
}

