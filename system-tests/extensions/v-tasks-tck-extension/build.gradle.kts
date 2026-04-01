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
    java
}

dependencies {
    api(libs.edc.spi.tasks)
    api(libs.edc.spi.core)
    api(libs.edc.spi.web)
    api(libs.edc.spi.dataplane)
    api(libs.edc.spi.transaction)
    api(libs.edc.spi.contract)
    api(libs.edc.spi.transfer)
    api(libs.edc.spi.controlplane)
    api(libs.edc.spi.participantcontext)
    api(libs.edc.spi.participantcontext.config)
    implementation(libs.nimbus.jwt)
}

