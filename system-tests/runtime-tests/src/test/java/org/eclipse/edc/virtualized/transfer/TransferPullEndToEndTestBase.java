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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.dataplane.spi.Endpoint;
import org.eclipse.edc.connector.dataplane.spi.iam.PublicEndpointGeneratorService;
import org.eclipse.edc.junit.annotations.Runtime;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.virtualized.Runtimes.ControlPlane;
import org.eclipse.edc.virtualized.transfer.fixtures.Participants;
import org.eclipse.edc.virtualized.transfer.fixtures.VirtualConnector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;


abstract class TransferPullEndToEndTestBase {

    protected static final String ASSET_ID = "asset-id";
    protected static final String POLICY_ID = "policy-id";

    @RegisterExtension
    static WireMockExtension callbacksEndpoint = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @BeforeAll
    static void beforeAll(PublicEndpointGeneratorService generatorService,
                          VirtualConnector connector,
                          Participants participants,
                          @Runtime(ControlPlane.NAME) Vault vault) {
        generatorService.addGeneratorFunction("HttpData", address -> Endpoint.url("http://example.com"));


        connector.createParticipant(participants.consumer().contextId(), participants.consumer().id(), participants.consumer().config());
        connector.createParticipant(participants.provider().contextId(), participants.provider().id(), participants.provider().config());

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
    void httpPull_dataTransfer(VirtualConnector env, Participants participants, TransferProcessService transferService) {
        var providerAddress = env.getProtocolEndpoint().get() + "/" + participants.provider().contextId() + "/2025-1";

        var assetId = setup(env, participants.provider());
        var transferProcessId = env.startTransfer(participants.consumer().contextId(), providerAddress, participants.provider().id(), assetId, "HttpData-PULL");

        var consumerTransfer = transferService.findById(transferProcessId);
        assertThat(consumerTransfer).isNotNull();

        var providerTransfer = transferService.findById(consumerTransfer.getCorrelationId());

        assertThat(providerTransfer).isNotNull();

        assertThat(consumerTransfer.getParticipantContextId()).isEqualTo(participants.consumer().contextId());
        assertThat(providerTransfer.getParticipantContextId()).isEqualTo(participants.provider().contextId());

    }

    private String setup(VirtualConnector env, Participants.Participant provider) {
        var asset = Asset.Builder.newInstance()
                .id(ASSET_ID)
                .dataAddress(DataAddress.Builder.newInstance().type("HttpData").build())
                .participantContextId(provider.contextId())
                .build();

        var policyDefinition = PolicyDefinition.Builder.newInstance()
                .id(POLICY_ID)
                .policy(Policy.Builder.newInstance().build())
                .participantContextId(provider.contextId())
                .build();

        env.setupResources(provider.contextId(), asset, policyDefinition, policyDefinition);

        return asset.getId();
    }

}


