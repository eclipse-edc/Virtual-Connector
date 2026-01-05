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
include(":spi:v-auth-spi")
include(":spi:v-core-spi")
include(":spi:v-task-spi")
include(":spi:iam:decentralized-claims:dcp-scope-spi")

// core
include(":core:v-connector-core")
include(":core:v-task-core")
include(":core:negotiation-manager")
include(":core:negotiation-task-executor")
include(":core:transfer-process-manager")
include(":core:transfer-process-task-executor")

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
include(":extensions:cel:cel-spi")
include(":extensions:cel:cel-extension")
include(":extensions:cel:cel-store-sql")
include(":extensions:cdc:memory:negotiation-cdc-memory")
include(":extensions:cdc:memory:transfer-process-cdc-memory")
include(":extensions:cdc:postgres:postgres-cdc")
include(":extensions:cdc:publisher:negotiation-cdc-publisher-nats")
include(":extensions:cdc:subscriber:negotiation-subscriber-nats")
include(":extensions:cdc:publisher:transfer-process-cdc-publisher-nats")
include(":extensions:cdc:subscriber:transfer-process-subscriber-nats")
include(":extensions:control-plane:tasks:listener:tasks-store-listener")
include(":extensions:control-plane:tasks:listener:tasks-store-poll-executor")
include(":extensions:control-plane:tasks:publisher:tasks-publisher-nats")
include(":extensions:control-plane:tasks:store:tasks-store-sql")
include(":extensions:control-plane:tasks:subscriber:negotiation-tasks-subscriber-nats")
include(":extensions:control-plane:tasks:subscriber:transfer-tasks-subscriber-nats")

include(":extensions:lib:nats-lib")
include(":extensions:common:api:api-authentication")
include(":extensions:common:api:api-authorization")
include(":extensions:common:banner-extension")
include(":extensions:iam:decentralized-claims:dcp-scope-core")

// APIs
include(":extensions:control-plane:api:management-api:asset-api")
include(":extensions:control-plane:api:management-api:catalog-api")
include(":extensions:control-plane:api:management-api:contract-definition-api")
include(":extensions:control-plane:api:management-api:policy-definition-api")
include(":extensions:control-plane:api:management-api:contract-negotiation-api")
include(":extensions:control-plane:api:management-api:contract-agreement-api")
include(":extensions:control-plane:api:management-api:transfer-process-api")
include(":extensions:control-plane:api:management-api:participant-context-api")
include(":extensions:control-plane:api:management-api:participant-context-config-api")
include(":extensions:control-plane:api:management-api:cel-api")
include(":extensions:control-plane:api:management-api:management-api-configuration")
include(":extensions:control-plane:api:management-api:v-management-api-schema-validator")

// lib
include(":extensions:common:api:lib:v-management-api-lib")

include(":system-tests:system-test-fixtures")
include(":system-tests:runtimes:issuer")
include(":system-tests:runtimes:identity-hub")
include(":system-tests:runtime-tests")
include(":system-tests:management-api:management-api-tests")
include(":system-tests:dsp-tck-tests")
include(":system-tests:extensions:v-tck-extension")
include(":system-tests:runtimes:tck:tck-controlplane-memory")
include(":system-tests:runtimes:tck:tck-controlplane-postgres")
include(":system-tests:runtimes:tck:tck-controlplane-postgres-events")
include(":system-tests:runtimes:e2e:e2e-controlplane-memory")
include(":system-tests:runtimes:e2e:e2e-controlplane-memory-tasks")
include(":system-tests:runtimes:e2e:e2e-controlplane-postgres")
include(":system-tests:runtimes:e2e:e2e-controlplane-postgres-tasks")
include(":system-tests:runtimes:e2e:e2e-controlplane-postgres-nats-tasks")
include(":system-tests:runtimes:e2e:e2e-dcp-controlplane-postgres")

// BOM modules ----------------------------------------------------------------
include(":dist:bom:virtual-controlplane-base-bom")
include(":dist:bom:virtual-controlplane-memory-bom")
include(":dist:bom:virtual-controlplane-cdc-base-bom")
include(":dist:bom:virtual-controlplane-feature-dcp-bom")
include(":dist:bom:virtual-controlplane-feature-sql-bom")
include(":dist:bom:virtual-controlplane-feature-nats-bom")
include(":dist:bom:virtual-controlplane-feature-nats-cdc-bom")

