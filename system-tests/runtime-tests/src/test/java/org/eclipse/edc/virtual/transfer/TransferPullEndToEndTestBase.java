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
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.test.system.utils.Participants;
import org.eclipse.edc.connector.controlplane.test.system.utils.client.ManagementApiClientV5;
import org.eclipse.edc.connector.controlplane.test.system.utils.client.api.model.AssetDto;
import org.eclipse.edc.connector.controlplane.test.system.utils.client.api.model.DataAddressDto;
import org.eclipse.edc.connector.controlplane.test.system.utils.client.api.model.DataPlaneRegistrationDto;
import org.eclipse.edc.connector.controlplane.test.system.utils.client.api.model.PermissionDto;
import org.eclipse.edc.connector.controlplane.test.system.utils.client.api.model.PolicyDefinitionDto;
import org.eclipse.edc.connector.controlplane.test.system.utils.client.api.model.PolicyDto;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.virtual.transfer.fixtures.VirtualConnector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.COMPLETED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.STARTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.SUSPENDED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.TERMINATED;


abstract class TransferPullEndToEndTestBase {

    @RegisterExtension
    static WireMockExtension callbacksEndpoint = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @BeforeAll
    static void beforeAll(
            ManagementApiClientV5 connectorClient,
            Participants participants) {

        connectorClient.createParticipant(participants.consumer().contextId(), participants.consumer().id(), participants.consumer().config());
        connectorClient.createParticipant(participants.provider().contextId(), participants.provider().id(), participants.provider().config());


        var consumerDp = new DataPlaneRegistrationDto(
                "consumer-dp",
                callbacksEndpoint.baseUrl(),
                Set.of("NonFinite-PULL"),
                Set.of(),
                null
        );
        connectorClient.dataplanes().registerDataPlane(participants.consumer().contextId(), consumerDp);

        var providerDp = new DataPlaneRegistrationDto(
                "provider-dp",
                callbacksEndpoint.baseUrl(),
                Set.of("NonFinite-PULL"),
                Set.of(),
                null
        );

        connectorClient.dataplanes().registerDataPlane(participants.provider().contextId(), providerDp);

    }

    @BeforeEach
    void beforeEach() {
        var prepared = """
                {
                    "dataplaneId" : "consumer-dp",
                    "state": "PREPARED"
                }
                """;
        callbacksEndpoint.stubFor(post("/prepare").willReturn(okJson(prepared)));

        var started = """
                {
                    "dataplaneId" : "consumer-dp",
                    "state": "STARTED"
                }
                """;
        callbacksEndpoint.stubFor(post("/start").willReturn(okJson(started)));

    }

    @Test
    void transfer(VirtualConnector env, ManagementApiClientV5 connectorClient, Participants participants) {
        var providerAddress = env.getProtocolEndpoint().get() + "/" + participants.provider().contextId() + "/2025-1";

        var assetId = setup(connectorClient, participants.provider());
        var transferProcessId = connectorClient.startTransfer(participants.consumer().contextId(), participants.provider().contextId(), providerAddress, participants.provider().id(), assetId, "NonFinite-PULL");

        var consumerTransfer = connectorClient.transfers().getTransferProcess(participants.consumer().contextId(), transferProcessId);
        var providerTransfer = connectorClient.transfers().getTransferProcess(participants.provider().contextId(), consumerTransfer.getCorrelationId());

        assertThat(consumerTransfer.getState()).isEqualTo(providerTransfer.getState());

    }

    @Test
    void suspendAndResumeByProvider(VirtualConnector env, ManagementApiClientV5 connectorClient, Participants participants) {
        var providerAddress = env.getProtocolEndpoint().get() + "/" + participants.provider().contextId() + "/2025-1";

        var assetId = setup(connectorClient, participants.provider());
        var transferProcessId = connectorClient.startTransfer(participants.consumer().contextId(), participants.provider().contextId(), providerAddress, participants.provider().id(), assetId, "NonFinite-PULL");

        var consumerTransfer = connectorClient.transfers().getTransferProcess(participants.consumer().contextId(), transferProcessId);
        var providerTransfer = connectorClient.transfers().getTransferProcess(participants.provider().contextId(), consumerTransfer.getCorrelationId());

        assertThat(consumerTransfer.getState()).isEqualTo(providerTransfer.getState());

        connectorClient.transfers().suspendTransfer(participants.provider().contextId(), consumerTransfer.getCorrelationId(), "Suspending for test");

        connectorClient.waitTransferInState(participants.consumer().contextId(), transferProcessId, SUSPENDED);
        connectorClient.waitTransferInState(participants.provider().contextId(), consumerTransfer.getCorrelationId(), SUSPENDED);


        connectorClient.transfers().resumeTransfer(participants.provider().contextId(), consumerTransfer.getCorrelationId());

        connectorClient.waitTransferInState(participants.consumer().contextId(), transferProcessId, STARTED);
        connectorClient.waitTransferInState(participants.provider().contextId(), consumerTransfer.getCorrelationId(), STARTED);
    }

    @Test
    void suspendAndResumeByConsumer(VirtualConnector env, ManagementApiClientV5 connectorClient, Participants participants) {
        var providerAddress = env.getProtocolEndpoint().get() + "/" + participants.provider().contextId() + "/2025-1";

        var assetId = setup(connectorClient, participants.provider());
        var transferProcessId = connectorClient.startTransfer(participants.consumer().contextId(), participants.provider().contextId(), providerAddress, participants.provider().id(), assetId, "NonFinite-PULL");

        var consumerTransfer = connectorClient.transfers().getTransferProcess(participants.consumer().contextId(), transferProcessId);
        var providerTransfer = connectorClient.transfers().getTransferProcess(participants.provider().contextId(), consumerTransfer.getCorrelationId());

        assertThat(consumerTransfer.getState()).isEqualTo(providerTransfer.getState());

        connectorClient.transfers().suspendTransfer(participants.consumer().contextId(), transferProcessId, "Suspending for test");

        connectorClient.waitTransferInState(participants.consumer().contextId(), transferProcessId, SUSPENDED);
        connectorClient.waitTransferInState(participants.provider().contextId(), consumerTransfer.getCorrelationId(), SUSPENDED);

        connectorClient.transfers().resumeTransfer(participants.consumer().contextId(), transferProcessId);

        connectorClient.waitTransferInState(participants.consumer().contextId(), transferProcessId, STARTED);
        connectorClient.waitTransferInState(participants.provider().contextId(), consumerTransfer.getCorrelationId(), STARTED);
    }

    @Test
    void terminateByConsumer(VirtualConnector env, ManagementApiClientV5 connectorClient, Participants participants) {
        var providerAddress = env.getProtocolEndpoint().get() + "/" + participants.provider().contextId() + "/2025-1";

        var assetId = setup(connectorClient, participants.provider());
        var transferProcessId = connectorClient.startTransfer(participants.consumer().contextId(), participants.provider().contextId(), providerAddress, participants.provider().id(), assetId, "NonFinite-PULL");

        var consumerTransfer = connectorClient.transfers().getTransferProcess(participants.consumer().contextId(), transferProcessId);
        var providerTransfer = connectorClient.transfers().getTransferProcess(participants.provider().contextId(), consumerTransfer.getCorrelationId());

        assertThat(consumerTransfer.getState()).isEqualTo(providerTransfer.getState());

        connectorClient.transfers().terminateTransfer(participants.consumer().contextId(), transferProcessId, "Terminate for test");

        connectorClient.waitTransferInState(participants.consumer().contextId(), transferProcessId, TERMINATED);
        connectorClient.waitTransferInState(participants.provider().contextId(), consumerTransfer.getCorrelationId(), TERMINATED);

    }

    @Test
    void terminateByProvider(VirtualConnector env, ManagementApiClientV5 connectorClient, Participants participants) {
        var providerAddress = env.getProtocolEndpoint().get() + "/" + participants.provider().contextId() + "/2025-1";

        var assetId = setup(connectorClient, participants.provider());
        var transferProcessId = connectorClient.startTransfer(participants.consumer().contextId(), participants.provider().contextId(), providerAddress, participants.provider().id(), assetId, "NonFinite-PULL");

        var consumerTransfer = connectorClient.transfers().getTransferProcess(participants.consumer().contextId(), transferProcessId);
        var providerTransfer = connectorClient.transfers().getTransferProcess(participants.provider().contextId(), consumerTransfer.getCorrelationId());

        assertThat(consumerTransfer.getState()).isEqualTo(providerTransfer.getState());

        connectorClient.transfers().terminateTransfer(participants.consumer().contextId(), transferProcessId, "Suspending for test");

        connectorClient.waitTransferInState(participants.consumer().contextId(), transferProcessId, TERMINATED);
        connectorClient.waitTransferInState(participants.provider().contextId(), consumerTransfer.getCorrelationId(), TERMINATED);

    }

    @Test
    void completeByProvider(VirtualConnector env, TransferProcessService service, ManagementApiClientV5 connectorClient, Participants participants) {
        var providerAddress = env.getProtocolEndpoint().get() + "/" + participants.provider().contextId() + "/2025-1";

        var assetId = setup(connectorClient, participants.provider());
        var transferProcessId = connectorClient.startTransfer(participants.consumer().contextId(), participants.provider().contextId(), providerAddress, participants.provider().id(), assetId, "NonFinite-PULL");

        var consumerTransfer = connectorClient.transfers().getTransferProcess(participants.consumer().contextId(), transferProcessId);
        var providerTransfer = connectorClient.transfers().getTransferProcess(participants.provider().contextId(), consumerTransfer.getCorrelationId());

        assertThat(consumerTransfer.getState()).isEqualTo(providerTransfer.getState());


        service.complete(consumerTransfer.getCorrelationId())
                .orElseThrow(f -> new EdcException(f.getFailureDetail()));

        connectorClient.waitTransferInState(participants.consumer().contextId(), transferProcessId, COMPLETED);
        connectorClient.waitTransferInState(participants.provider().contextId(), consumerTransfer.getCorrelationId(), COMPLETED);

    }

    @Test
    void completeByConsumer(VirtualConnector env, TransferProcessService service, ManagementApiClientV5 connectorClient, Participants participants) {
        var providerAddress = env.getProtocolEndpoint().get() + "/" + participants.provider().contextId() + "/2025-1";

        var assetId = setup(connectorClient, participants.provider());
        var transferProcessId = connectorClient.startTransfer(participants.consumer().contextId(), participants.provider().contextId(), providerAddress, participants.provider().id(), assetId, "NonFinite-PULL");

        var consumerTransfer = connectorClient.transfers().getTransferProcess(participants.consumer().contextId(), transferProcessId);
        var providerTransfer = connectorClient.transfers().getTransferProcess(participants.provider().contextId(), consumerTransfer.getCorrelationId());

        assertThat(consumerTransfer.getState()).isEqualTo(providerTransfer.getState());


        service.complete(consumerTransfer.getId())
                .orElseThrow(f -> new EdcException(f.getFailureDetail()));

        connectorClient.waitTransferInState(participants.consumer().contextId(), transferProcessId, COMPLETED);
        connectorClient.waitTransferInState(participants.provider().contextId(), consumerTransfer.getCorrelationId(), COMPLETED);

    }

    private String setup(ManagementApiClientV5 connectorClient, Participants.Participant provider) {
        var asset = new AssetDto(new DataAddressDto("HttpData"));

        var permissions = List.of(new PermissionDto());
        var policyDef = new PolicyDefinitionDto(new PolicyDto(permissions));

        return connectorClient.setupResources(provider.contextId(), asset, policyDef, policyDef);

    }

}


