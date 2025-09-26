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
    api(project(":data-protocols:dsp:dsp-2025"))
    api(libs.edc.core.dsp.http)
    api(libs.edc.core.dsp.http.configuration)
    api(libs.edc.core.dsp.http.catalog.dispatcher)
    api(libs.edc.core.dsp.http.negotiation.dispatcher)
    api(libs.edc.core.dsp.http.transferprocess.dispatcher)
    api(libs.edc.core.dsp.version.http.api)
}
