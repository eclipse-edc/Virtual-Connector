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

rootProject.name = "edc-v"

// this is needed to have access to snapshot builds of plugins
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
    }
}

// spi
include(":spi:v-core-spi")

// core
include(":core:v-connector-core")
include(":core:negotiation-manager")
include(":core:transfer-process-manager")

// data-protocols
include(":data-protocols:dsp")
include(":data-protocols:dsp:dsp-2025")
include(":data-protocols:dsp:dsp-2025:dsp-catalog-2025")
include(":data-protocols:dsp:dsp-2025:dsp-catalog-2025:dsp-catalog-http-api-2025")
include(":data-protocols:dsp:dsp-2025:dsp-negotiation-2025")
include(":data-protocols:dsp:dsp-2025:dsp-negotiation-2025:dsp-negotiation-http-api-2025")
include(":data-protocols:dsp:dsp-2025:dsp-transfer-process-2025")
include(":data-protocols:dsp:dsp-2025:dsp-transfer-process-2025:dsp-transfer-process-http-api-2025")
// extensions
include(":extensions:banner-extension")
include(":extensions:negotiation-cdc-memory")
include(":extensions:transfer-process-cdc-memory")
include(":extensions:postgres-cdc")
include(":extensions:negotiation-cdc-publisher-nats")
include(":extensions:negotiation-subscriber-nats")
include(":extensions:transfer-process-cdc-publisher-nats")
include(":extensions:transfer-process-subscriber-nats")
include(":extensions:lib:nats-lib")

include(":system-tests:runtimes:controlplane-base")
include(":system-tests:runtimes:controlplane-memory")
include(":system-tests:runtimes:controlplane-postgres")
include(":system-tests:runtime-tests")
include(":system-tests:dsp-tck-tests")
include(":system-tests:extensions:v-tck-extension")
include(":system-tests:runtimes:tck:tck-controlplane-memory")
include(":system-tests:runtimes:tck:tck-controlplane-postgres")

