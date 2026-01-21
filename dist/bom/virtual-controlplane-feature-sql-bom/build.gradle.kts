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
    api(project(":dist:bom:virtual-controlplane-base-bom"))
    api(project(":extensions:cel:cel-store-sql"))
    api(project(":extensions:control-plane:tasks:store:tasks-store-sql"))
    runtimeOnly(libs.edc.bom.controlplane.sql)
    runtimeOnly(libs.edc.participantcontext.store.sql)
    runtimeOnly(libs.edc.participantcontext.config.store.sql)
}

