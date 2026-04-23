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


// extensions
include(":extensions:control-plane:tasks:listener:tasks-store-poll-executor")
include(":extensions:common:banner-extension")

// lib

include(":system-tests:system-test-fixtures")
include(":system-tests:runtimes:issuer")
include(":system-tests:runtimes:identity-hub")
include(":system-tests:runtime-tests")
include(":system-tests:dsp-tck-tests")
include(":system-tests:extensions:v-tck-extension")
include(":system-tests:extensions:v-tasks-tck-extension")
include(":system-tests:runtimes:tck:tck-controlplane-memory")
include(":system-tests:runtimes:tck:tck-controlplane-postgres")
include(":system-tests:runtimes:e2e:e2e-controlplane-memory")
include(":system-tests:runtimes:e2e:e2e-controlplane-postgres")
include(":system-tests:runtimes:e2e:e2e-controlplane-postgres-nats")
include(":system-tests:runtimes:e2e:e2e-dcp-controlplane-postgres")

// BOM modules ----------------------------------------------------------------
include(":dist:bom:virtual-controlplane-base-bom")
include(":dist:bom:virtual-controlplane-memory-bom")
include(":dist:bom:virtual-controlplane-feature-dcp-bom")
include(":dist:bom:virtual-controlplane-feature-sql-bom")
include(":dist:bom:virtual-controlplane-feature-nats-bom")
