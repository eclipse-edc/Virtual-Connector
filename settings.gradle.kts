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
include(":spi:auth-spi")
include(":spi:v-core-spi")

// core
include(":core:v-connector-core")
include(":core:negotiation-manager")
include(":core:transfer-process-manager")

// data-protocols
include(":data-protocols:dsp")
include(":data-protocols:dsp:dsp-metadata-http-api")
include(":data-protocols:dsp:dsp-2025")
include(":data-protocols:dsp:dsp-2025:dsp-http-api-configuration-2025")
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
include(":extensions:common:api:api-authentication")
include(":extensions:common:api:api-authorization")

// APIs
include(":extensions:control-plane:api:management-api:asset-api")
include(":extensions:control-plane:api:management-api:management-api-configuration")

// lib
include(":extensions:common:api:lib:management-api-lib")
include(":extensions:common:api:lib:api-authorization-lib")
include(":extensions:common:api:lib:api-authentication-lib")

include(":system-tests:system-test-fixtures")
include(":system-tests:runtimes:controlplane-base")
include(":system-tests:runtimes:controlplane-memory")
include(":system-tests:runtimes:controlplane-postgres")
include(":system-tests:runtimes:issuer")
include(":system-tests:runtimes:identity-hub")
include(":system-tests:runtime-tests")
include(":system-tests:management-api:management-api-tests")
include(":system-tests:dsp-tck-tests")
include(":system-tests:extensions:v-tck-extension")
include(":system-tests:runtimes:tck:tck-controlplane-memory")
include(":system-tests:runtimes:tck:tck-controlplane-postgres")
include(":system-tests:runtimes:e2e:e2e-controlplane-memory")
include(":system-tests:runtimes:e2e:e2e-controlplane-postgres")
include(":system-tests:runtimes:e2e:e2e-dcp-controlplane-postgres")



