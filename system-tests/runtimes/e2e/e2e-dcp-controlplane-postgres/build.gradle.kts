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
    implementation(project(":extensions:iam:decentralized-claims:dcp-scope-core"))
    implementation(project(":system-tests:runtimes:controlplane-postgres")) {
        exclude("org.eclipse.edc", "vault-hashicorp")
    }
    runtimeOnly(libs.edc.bom.dataplane) {
        exclude(module = "data-plane-selector-client")
    }
    implementation(project(":extensions:postgres-cdc"))
    implementation(project(":extensions:negotiation-cdc-publisher-nats"))
    implementation(project(":extensions:negotiation-subscriber-nats"))
    implementation(project(":extensions:transfer-process-cdc-publisher-nats"))
    implementation(project(":extensions:transfer-process-subscriber-nats"))

    runtimeOnly(libs.parsson)
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}

edcBuild {
    publish.set(false)
}
