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

package org.eclipse.edc.virtual.transfer;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.eclipse.edc.connector.dataplane.spi.Endpoint;
import org.eclipse.edc.connector.dataplane.spi.iam.PublicEndpointGeneratorService;
import org.eclipse.edc.junit.annotations.Runtime;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.virtual.Runtimes.ControlPlane;
import org.eclipse.edc.virtual.transfer.fixtures.Participants;
import org.eclipse.edc.virtual.transfer.fixtures.VirtualConnector;
import org.eclipse.edc.virtual.transfer.fixtures.VirtualConnectorClient;
import org.eclipse.edc.virtual.transfer.fixtures.api.model.AssetDto;
import org.eclipse.edc.virtual.transfer.fixtures.api.model.DataAddressDto;
import org.eclipse.edc.virtual.transfer.fixtures.api.model.PermissionDto;
import org.eclipse.edc.virtual.transfer.fixtures.api.model.PolicyDefinitionDto;
import org.eclipse.edc.virtual.transfer.fixtures.api.model.PolicyDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;


abstract class TransferPullEndToEndTestBase {

    @RegisterExtension
    static WireMockExtension callbacksEndpoint = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @BeforeAll
    static void beforeAll(PublicEndpointGeneratorService generatorService,
                          VirtualConnectorClient connectorClient,
                          Participants participants,
                          @Runtime(ControlPlane.NAME) Vault vault) {
        generatorService.addGeneratorFunction("HttpData", address -> Endpoint.url("http://example.com"));


        connectorClient.createParticipant(participants.consumer().contextId(), participants.consumer().id(), participants.consumer().config());
        connectorClient.createParticipant(participants.provider().contextId(), participants.provider().id(), participants.provider().config());

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
    void httpPull_dataTransfer(VirtualConnector env, VirtualConnectorClient connectorClient, Participants participants) {
        var providerAddress = env.getProtocolEndpoint().get() + "/" + participants.provider().contextId() + "/2025-1";

        var assetId = setup(connectorClient, participants.provider());
        var transferProcessId = connectorClient.startTransfer(participants.consumer().contextId(), participants.provider().contextId(), providerAddress, participants.provider().id(), assetId, "HttpData-PULL");

        var consumerTransfer = connectorClient.transfers().getTransferProcess(participants.consumer().contextId(), transferProcessId);
        var providerTransfer = connectorClient.transfers().getTransferProcess(participants.provider().contextId(), consumerTransfer.getCorrelationId());

        assertThat(consumerTransfer.getState()).isEqualTo(providerTransfer.getState());

    }

    private String setup(VirtualConnectorClient connectorClient, Participants.Participant provider) {
        var asset = new AssetDto(new DataAddressDto("HttpData"));

        var permissions = List.of(new PermissionDto());
        var policyDef = new PolicyDefinitionDto(new PolicyDto(permissions));

        return connectorClient.setupResources(provider.contextId(), asset, policyDef, policyDef);

    }

}


