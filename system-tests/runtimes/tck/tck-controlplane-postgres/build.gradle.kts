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

    implementation(project(":system-tests:extensions:v-tck-extension"));
    implementation(project(":system-tests:runtimes:controlplane-postgres")) {
        exclude("org.eclipse.edc", "decentralized-claims-service")
        exclude("org.eclipse.edc", "decentralized-claims-core")
        exclude("org.eclipse.edc", "decentralized-claims-sts-remote-client")
        exclude("org.eclipse.edc", "decentralized-claims-issuers-configuration")
        exclude("org.eclipse.edc", "vault-hashicorp")
    }
    implementation(project(":extensions:postgres-cdc"))
    implementation(project(":extensions:negotiation-cdc-publisher-nats"))
    implementation(project(":extensions:negotiation-subscriber-nats"))
    implementation(project(":extensions:transfer-process-cdc-publisher-nats"))
    implementation(project(":extensions:transfer-process-subscriber-nats"))
    runtimeOnly(libs.edc.tck.extension)
    runtimeOnly(libs.edc.core.participantcontext.single)
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
