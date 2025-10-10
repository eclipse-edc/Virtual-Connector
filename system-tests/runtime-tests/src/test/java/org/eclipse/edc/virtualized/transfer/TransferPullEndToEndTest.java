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

package org.eclipse.edc.virtualized.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.dataplane.spi.Endpoint;
import org.eclipse.edc.connector.dataplane.spi.iam.PublicEndpointGeneratorService;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.virtualized.fixtures.ControlPlaneExtension;
import org.eclipse.edc.virtualized.fixtures.ControlPlaneRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.eclipse.edc.virtualized.transfer.TestFunctions.PROVIDER_CONTEXT;
import static org.eclipse.edc.virtualized.transfer.TestFunctions.setupControlPlane;
import static org.eclipse.edc.virtualized.transfer.TestFunctions.startTransfer;


class TransferPullEndToEndTest {

    abstract static class Tests {

        private static final ObjectMapper MAPPER = new ObjectMapper();
        private static final String ASSET_ID = "asset-id";
        private static final String POLICY_ID = "policy-id";

        @RegisterExtension
        static WireMockExtension callbacksEndpoint = WireMockExtension.newInstance()
                .options(wireMockConfig().dynamicPort())
                .build();

        @BeforeAll
        static void beforeAll(ControlPlaneRuntime cp) {

            var generatorService = cp.getService(PublicEndpointGeneratorService.class);
            var vault = cp.getService(Vault.class);

            generatorService.addGeneratorFunction("HttpData", address -> Endpoint.url("http://example.com"));

            try {
                var key = new ECKeyGenerator(Curve.P_256)
                        .keyID("sign-key")
                        .generate();
                vault.storeSecret("private-key", key.toJSONString());
                vault.storeSecret("public-key", key.toPublicJWK().toJSONString());
            } catch (JOSEException e) {
                throw new RuntimeException(e);
            }

        }

        @Test
        void httpPull_dataTransfer(ControlPlaneRuntime cp) throws IOException {
            var assetId = setup(cp);
            var transferProcessId = startTransfer(cp, assetId, "HttpData-PULL");
            var transferService = cp.getService(TransferProcessService.class);

            var consumerTransfer = transferService.findById(transferProcessId);

        }

        private String setup(ControlPlaneRuntime cp) {
            var asset = Asset.Builder.newInstance()
                    .id(ASSET_ID)
                    .dataAddress(DataAddress.Builder.newInstance().type("HttpData").build())
                    .participantContextId(PROVIDER_CONTEXT)
                    .build();

            var policyDefinition = PolicyDefinition.Builder.newInstance()
                    .id(POLICY_ID)
                    .policy(Policy.Builder.newInstance().build())
                    .participantContextId(PROVIDER_CONTEXT)
                    .build();
            setupControlPlane(cp, asset, policyDefinition);

            return asset.getId();
        }

    }

    @Nested
    @EndToEndTest
    class InMemory extends Tests {

        @RegisterExtension
        static final ControlPlaneExtension CONTROL_PLANE = ControlPlaneExtension.Builder.newInstance()
                .id("controlplane")
                .name("controlplane")
                .modules(":system-tests:runtimes:e2e:e2e-controlplane-memory")
                .build();
    }

}
