/*
 *  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

plugins {
    java
}

dependencies {
    testImplementation(project(":spi:auth-spi"))
    testImplementation(libs.edc.junit)
    testImplementation(libs.edc.spi.asset)
    testImplementation(libs.edc.spi.contract)
    testImplementation(libs.edc.spi.transfer)

    // gives access to the Json LD models, etc.
    testImplementation(libs.edc.spi.jsonld)

//    testImplementation(project(":data-protocols:dsp:dsp-spi"))
//    testImplementation(project(":data-protocols:dsp:dsp-2025:dsp-spi-2025"))
//    testImplementation(project(":spi:control-plane:asset-spi"))
//    testImplementation(project(":spi:control-plane:contract-spi"))
//    testImplementation(project(":spi:data-plane-selector:data-plane-selector-spi"))
//    testImplementation(project(":core:common:connector-core"))
//    testImplementation(project(":core:common:edr-store-core"))
//
//    //useful for generic DTOs etc.
//    testImplementation(project(":spi:control-plane:policy-spi"))
//    testImplementation(project(":spi:control-plane:transfer-spi"))
//
//    //we need the JacksonJsonLd util class
//    testImplementation(project(":core:common:lib:json-ld-lib"))
//    testImplementation(project(":core:common:lib:api-lib"))
//    testImplementation(project(":extensions:common:json-ld"))
//
    testImplementation(libs.restAssured)
    testImplementation(libs.awaitility)
    testImplementation(libs.nimbus.jwt)
    testImplementation(libs.wiremock)
    testImplementation(testFixtures(libs.edc.fixtures.sql))

//    testImplementation(project(":extensions:common:transaction:transaction-local"))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(testFixtures(project(":extensions:lib:nats-lib")))
    testImplementation(project(":system-tests:system-test-fixtures"))

//
//    testImplementation(project(":extensions:control-plane:api:management-api:contract-definition-api"))
//    testImplementation(project(":extensions:control-plane:api:management-api:contract-negotiation-api"))
//    testImplementation(project(":extensions:control-plane:api:management-api:policy-definition-api"))
//    testImplementation(project(":extensions:control-plane:api:management-api:transfer-process-api"))
//    testImplementation(project(":extensions:control-plane:api:management-api:secrets-api"))
//    testImplementation(project(":extensions:control-plane:transfer:transfer-data-plane-signaling"))
//    testImplementation(project(":extensions:control-plane:api:management-api:edr-cache-api"))
//    testImplementation(project(":extensions:data-plane-selector:data-plane-selector-api"))
    testRuntimeOnly(libs.bouncyCastle.bcprovJdk18on)

}

edcBuild {
    publish.set(false)
}
