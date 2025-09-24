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
    runtimeOnly(project(":extensions:banner-extension"))
    runtimeOnly(libs.edc.bom.controlplane)
    // uncomment the following lines to compile with Hashicorp Vault and Postgres persistence
    // runtimeOnly(libs.edc.vault.hashicorp)
    // runtimeOnly(libs.edc.bom.controlplane.sql)
}



