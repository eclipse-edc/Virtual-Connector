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
    testImplementation(project(":extensions:cel:cel-spi"))
    testImplementation(libs.awaitility)
    testImplementation(libs.edc.spi.dataplane)
    testImplementation(libs.edc.spi.jsonld)
    testImplementation(libs.edc.spi.transaction.datasource)
    testImplementation(libs.edc.spi.controlplane)
    testImplementation(libs.edc.spi.participantcontext.config)
    testImplementation(libs.edc.lib.jsonld)
    testImplementation(libs.edc.lib.sql)
    testImplementation(libs.edc.junit)
    testImplementation(libs.restAssured)
    testImplementation(testFixtures(libs.edc.fixtures.sql))
    testImplementation(testFixtures(project(":extensions:lib:nats-lib")))
    testImplementation(project(":system-tests:system-test-fixtures"))
    testImplementation(testFixtures(libs.edc.ih.test.fixtures)) {
        exclude("com.networknt", "json-schema-validator")
    }
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.vault)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.wiremock) {
        exclude("com.networknt", "json-schema-validator")
    }
    testImplementation(testFixtures(libs.edc.lib.oauth2.authn))
    testImplementation(libs.nimbus.jwt)
    testImplementation(libs.bouncyCastle.bcpkixJdk18on)

    testCompileOnly(project(":system-tests:runtimes:e2e:e2e-controlplane-memory"))
    testCompileOnly(project(":system-tests:runtimes:e2e:e2e-controlplane-memory-tasks"))
    testCompileOnly(project(":system-tests:runtimes:e2e:e2e-controlplane-postgres"))
    testCompileOnly(project(":system-tests:runtimes:e2e:e2e-controlplane-postgres-nats-tasks"))
    testCompileOnly(project(":system-tests:runtimes:e2e:e2e-dcp-controlplane-postgres"))
    testCompileOnly(project(":system-tests:runtimes:issuer"))
    testCompileOnly(project(":system-tests:runtimes:identity-hub"))

}

edcBuild {
    publish.set(false)
}



