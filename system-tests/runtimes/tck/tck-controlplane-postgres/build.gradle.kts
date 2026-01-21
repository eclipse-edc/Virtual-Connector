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
    id("application")
}

dependencies {

    implementation(project(":system-tests:extensions:v-tasks-tck-extension"))
    implementation(project(":dist:bom:virtual-controlplane-base-bom"))
    implementation(project(":dist:bom:virtual-controlplane-feature-sql-bom"))
    implementation(project(":dist:bom:virtual-controlplane-feature-nats-bom"))
    runtimeOnly(libs.edc.bom.dataplane) {
        exclude(module = "data-plane-selector-client")
    }
    runtimeOnly(libs.edc.iam.mock)
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}

edcBuild {
    publish.set(false)
}
