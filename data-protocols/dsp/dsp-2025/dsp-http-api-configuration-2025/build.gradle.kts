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
    api(libs.edc.spi.protocol)
    api(libs.edc.spi.jsonld)
    api(libs.edc.spi.dsp.http)
    api(libs.edc.spi.dsp.v2025)
    implementation(libs.edc.lib.transform)
    implementation(libs.edc.lib.controlplane.transform)
}
