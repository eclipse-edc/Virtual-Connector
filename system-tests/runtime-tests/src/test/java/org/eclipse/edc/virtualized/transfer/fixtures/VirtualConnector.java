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

package org.eclipse.edc.virtualized.transfer.fixtures;

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
import org.eclipse.edc.junit.extensions.ComponentRuntimeContext;
import org.eclipse.edc.junit.utils.LazySupplier;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.FINALIZED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.STARTED;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

public class VirtualConnector {

    protected static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String PROTOCOL = "dataspace-protocol-http:2025-1";
    private static final JsonLdNamespace NS = new JsonLdNamespace(EDC_NAMESPACE);
    private static final ObjectMapper MAPPER = JacksonJsonLd.createObjectMapper();
    private final Function<Class<?>, ?> serviceLocator;
    private final ParticipantContextService contextService;
    private final ParticipantContextConfigService contextConfigService;
    private final AssetService assetService;
    private final PolicyDefinitionService policyService;
    private final ContractDefinitionService contractDefinitionService;
    private final CatalogService catalogService;
    private final ContractNegotiationService negotiationService;
    private final TransferProcessService transferProcessService;
    private final LazySupplier<URI> protocolEndpoint;

    public VirtualConnector(Function<Class<?>, ?> serviceLocator, ParticipantContextService contextService,
                            ParticipantContextConfigService contextConfigService, AssetService assetService,
                            PolicyDefinitionService policyService, ContractDefinitionService contractDefinitionService,
                            CatalogService catalogService, ContractNegotiationService negotiationService, TransferProcessService transferProcessService,
                            LazySupplier<URI> protocolEndpoint) {
        this.serviceLocator = serviceLocator;
        this.contextService = contextService;
        this.contextConfigService = contextConfigService;
        this.assetService = assetService;
        this.policyService = policyService;
        this.contractDefinitionService = contractDefinitionService;
        this.catalogService = catalogService;
        this.negotiationService = negotiationService;
        this.transferProcessService = transferProcessService;
        this.protocolEndpoint = protocolEndpoint;
    }

    public static VirtualConnector forContext(ComponentRuntimeContext ctx) {
        return new VirtualConnector(
                ctx::getService,
                ctx.getService(ParticipantContextService.class),
                ctx.getService(ParticipantContextConfigService.class),
                ctx.getService(AssetService.class),
                ctx.getService(PolicyDefinitionService.class),
                ctx.getService(ContractDefinitionService.class),
                ctx.getService(CatalogService.class),
                ctx.getService(ContractNegotiationService.class),
                ctx.getService(TransferProcessService.class),
                ctx.getEndpoint("protocol")
        );
    }

    public LazySupplier<URI> getProtocolEndpoint() {
        return protocolEndpoint;
    }

    private String startTransferProcess(String participantContext, String contractAgreementId, String providerAddress, String transferType) {
        var transferRequest = TransferRequest.Builder.newInstance()
                .protocol(PROTOCOL)
                .counterPartyAddress(providerAddress)
                .transferType(transferType)
                .contractId(contractAgreementId)
                .build();

        var transfer = transferProcessService.initiateTransfer(new ParticipantContext(participantContext), transferRequest)
                .getContent();

        await().atMost(TIMEOUT).untilAsserted(() -> {
            var state = transferProcessService.getState(transfer.getId());
            assertThat(state).isEqualTo(STARTED.name());
        });

        return transfer.getId();

    }

    private String startContractNegotiation(String participantContext, String assetId, String offerId, String providerAddress, String providerId) {
        return startContractNegotiation(participantContext, assetId, offerId, Policy.Builder.newInstance().build(), providerAddress, providerId);
    }

    private String startContractNegotiation(String participantContext, String assetId, String offerId, Policy policy, String providerAddress, String providerId) {
        var newPolicy = policy.toBuilder()
                .assigner(providerId)
                .target(assetId)
                .build();
        var contractRequest = ContractRequest.Builder.newInstance()
                .protocol(PROTOCOL)
                .counterPartyAddress(providerAddress)
                .contractOffer(ContractOffer.Builder.newInstance()
                        .id(offerId)
                        .assetId(assetId)
                        .policy(newPolicy)
                        .build())
                .build();

        var negotiation = negotiationService.initiateNegotiation(new ParticipantContext(participantContext), contractRequest);

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

    public String startTransfer(String participantContext, String providerAddress, String providerId, String assetId, String transferType) {
        return startTransfer(participantContext, providerAddress, providerId, assetId, transferType, Policy.Builder.newInstance().build());

    }

    public String startTransfer(String participantContext, String providerAddress, String providerId, String assetId, String transferType, Policy policy) {

        try {
            var asset = catalogService.requestDataset(new ParticipantContext(participantContext), assetId, providerId, providerAddress, PROTOCOL).get();
            var responseBody = MAPPER.readValue(asset.getContent(), JsonObject.class);
            var offerId = responseBody.getJsonArray("hasPolicy").getJsonObject(0).getString(ID);

            var agreementId = startContractNegotiation(participantContext, assetId, offerId, policy, providerAddress, providerId);

            return startTransferProcess(participantContext, agreementId, providerAddress, transferType);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setupResources(String participantContext, Asset asset, PolicyDefinition accessPolicy, PolicyDefinition contractPolicy) {
        assetService.create(asset);

        policyService.create(contractPolicy);
        policyService.create(accessPolicy);

        var contractDefinition = ContractDefinition.Builder.newInstance()
                .accessPolicyId(accessPolicy.getId())
                .contractPolicyId(contractPolicy.getId())
                .assetsSelectorCriterion(Criterion.criterion(NS.toIri("id"), "=", asset.getId()))
                .participantContextId(participantContext)
                .build();
        contractDefinitionService.create(contractDefinition);
    }

    public void createParticipant(String participantContextId, String participantId) {
        createParticipant(participantContextId, participantId, Map.of());
    }

    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> serviceClass) {
        return (T) serviceLocator.apply(serviceClass);
    }

    public void createParticipant(String participantContextId, String participantId, Map<String, String> cfg) {
        var configuration = new HashMap<>(cfg);
        configuration.put("edc.participant.id", participantId);
        contextService.createParticipantContext(new ParticipantContext(participantContextId))
                .orElseThrow(e -> new RuntimeException(e.getFailureDetail()));
        contextConfigService.save(participantContextId, ConfigFactory.fromMap(configuration))
                .orElseThrow(e -> new RuntimeException(e.getFailureDetail()));
    }
}
