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
    runtimeOnly(project(":system-tests:runtimes:controlplane-memory")) {
        exclude("org.eclipse.edc", "identity-trust-service")
        exclude("org.eclipse.edc", "identity-trust-core")
        exclude("org.eclipse.edc", "identity-trust-sts-remote-client")
        exclude("org.eclipse.edc", "identity-trust-issuers-configuration")
    }
    runtimeOnly(libs.edc.tck.extension)
    runtimeOnly(libs.edc.bom.dataplane) {
        exclude(module = "data-plane-selector-client")
    }
    runtimeOnly(libs.edc.iam.mock)
    runtimeOnly(libs.parsson)
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}

edcBuild {
    publish.set(false)
}
