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
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequest;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.services.spi.asset.AssetService;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogService;
import org.eclipse.edc.connector.controlplane.services.spi.contractdefinition.ContractDefinitionService;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.connector.controlplane.services.spi.policydefinition.PolicyDefinitionService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferRequest;
import org.eclipse.edc.jsonld.spi.JsonLdNamespace;
import org.eclipse.edc.jsonld.util.JacksonJsonLd;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.virtualized.fixtures.ControlPlaneRuntime;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.FINALIZED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.STARTED;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

public class TestFunctions {

    public static final String PROVIDER_CONTEXT = "provider";
    public static final String CONSUMER_CONTEXT = "consumer";
    protected static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String PROTOCOL = "dataspace-protocol-http:2025-1";
    private static final JsonLdNamespace NS = new JsonLdNamespace(EDC_NAMESPACE);
    private static final ObjectMapper MAPPER = JacksonJsonLd.createObjectMapper();

    public static void setupControlPlane(ControlPlaneRuntime cp, Asset asset, PolicyDefinition policyDefinition) {
        setupParticipantContext(cp);
        setupResources(cp, asset, policyDefinition, policyDefinition);
    }

    public static void setupParticipantContext(ControlPlaneRuntime cp) {
        var participantService = cp.getService(ParticipantContextService.class);
        participantService.createParticipantContext(new ParticipantContext(PROVIDER_CONTEXT));
        participantService.createParticipantContext(new ParticipantContext(CONSUMER_CONTEXT));
    }

    public static void setupResources(ControlPlaneRuntime cp, Asset asset, PolicyDefinition accessPolicy, PolicyDefinition contractPolicy) {
        var assetService = cp.getService(AssetService.class);
        assetService.create(asset);

        var policyService = cp.getService(PolicyDefinitionService.class);
        policyService.create(contractPolicy);
        policyService.create(accessPolicy);

        var contractDefinitionService = cp.getService(ContractDefinitionService.class);
        var contractDefinition = ContractDefinition.Builder.newInstance()
                .accessPolicyId(accessPolicy.getId())
                .contractPolicyId(contractPolicy.getId())
                .assetsSelectorCriterion(Criterion.criterion(NS.toIri("id"), "=", asset.getId()))
                .participantContextId(PROVIDER_CONTEXT)
                .build();
        contractDefinitionService.create(contractDefinition);
    }

    public static String startTransfer(ControlPlaneRuntime cp, String assetId, String transferType) {
        var providerAddress = cp.getControlPlaneProtocol() + "/" + PROVIDER_CONTEXT + "/2025-1";
        var catalogService = cp.getService(CatalogService.class);

        try {
            var asset = catalogService.requestDataset(new ParticipantContext(CONSUMER_CONTEXT), assetId, PROVIDER_CONTEXT, providerAddress, PROTOCOL).get();
            var responseBody = MAPPER.readValue(asset.getContent(), JsonObject.class);
            var offerId = responseBody.getJsonArray("hasPolicy").getJsonObject(0).getString(ID);

            var agreementId = startContractNegotiation(cp, assetId, offerId, providerAddress);

            return startTransferProcess(cp, agreementId, providerAddress, transferType);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String startContractNegotiation(ControlPlaneRuntime cp, String assetId, String offerId, String providerAddress) {
        var negotiationService = cp.getService(ContractNegotiationService.class);
        var contractRequest = ContractRequest.Builder.newInstance()
                .protocol(PROTOCOL)
                .counterPartyAddress(providerAddress)
                .contractOffer(ContractOffer.Builder.newInstance()
                        .id(offerId)
                        .assetId(assetId)
                        .policy(Policy.Builder.newInstance()
                                .assigner(PROVIDER_CONTEXT)
                                .target(assetId)
                                .build())
                        .build())
                .build();

        var negotiation = negotiationService.initiateNegotiation(new ParticipantContext(CONSUMER_CONTEXT), contractRequest);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            var state = negotiationService.getState(negotiation.getId());
            assertThat(state).isEqualTo(FINALIZED.name());
        });

        await().atMost(TIMEOUT).untilAsserted(() -> {
            var query = QuerySpec.Builder.newInstance()
                    .filter(Criterion.criterion("correlationId", "=", negotiation.getId())).build();

            var state = negotiationService.search(query)
                    .getContent().stream().findFirst();
            assertThat(state.get().getState()).isEqualTo(FINALIZED.code());
        });

        return negotiationService.getForNegotiation(negotiation.getId()).getId();

    }

    private static String startTransferProcess(ControlPlaneRuntime cp, String contractAgreementId, String providerAddress, String transferType) {
        var transferProcessService = cp.getService(TransferProcessService.class);
        var transferRequest = TransferRequest.Builder.newInstance()
                .protocol(PROTOCOL)
                .counterPartyAddress(providerAddress)
                .transferType(transferType)
                .contractId(contractAgreementId)
                .build();

        var transfer = transferProcessService.initiateTransfer(new ParticipantContext(CONSUMER_CONTEXT), transferRequest)
                .getContent();

        await().atMost(TIMEOUT).untilAsserted(() -> {
            var state = transferProcessService.getState(transfer.getId());
            assertThat(state).isEqualTo(STARTED.name());
        });

        return transfer.getId();

    }
}
