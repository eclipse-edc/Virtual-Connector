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
    runtimeOnly(libs.edc.core.runtime)
    runtimeOnly(libs.edc.core.jersey)
    runtimeOnly(libs.edc.core.jetty)
    runtimeOnly(libs.edc.core.sql)
    runtimeOnly(libs.edc.transaction.local)
    runtimeOnly(libs.edc.sql.pool)
    runtimeOnly(libs.edc.api.observability)
}



