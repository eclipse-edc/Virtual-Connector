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

package org.eclipse.edc.test.e2e.managementapi.v4;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.selector.spi.store.DataPlaneInstanceStore;
import org.eclipse.edc.junit.annotations.ApiTest;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.annotations.PostgresqlIntegrationTest;
import org.eclipse.edc.junit.extensions.ComponentRuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.participantcontext.spi.config.model.ParticipantContextConfiguration;
import org.eclipse.edc.participantcontext.spi.config.store.ParticipantContextConfigStore;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContextState;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndExtension;
import org.eclipse.edc.test.e2e.managementapi.ManagementEndToEndTestContext;
import org.eclipse.edc.test.e2e.managementapi.Runtimes;
import org.eclipse.edc.virtualized.nats.testfixtures.NatsEndToEndExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.http.ContentType.JSON;
import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.virtualized.test.system.fixtures.DockerImages.createPgContainer;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesRegex;

@ApiTest
public class CatalogApiV4EndToEndTest {

    @SuppressWarnings("JUnitMalformedDeclaration")
    abstract static class Tests {
        private static final String PARTICIPANT_CONTEXT_ID = "test-participant";
        /**
         * This means that all Catalog requests will ultimately loop back to this runtime's own DSP API
         */
        public static final String COUNTER_PARTY_ID = PARTICIPANT_CONTEXT_ID;
        @Order(0)
        @RegisterExtension
        static WireMockExtension mockJwksServer = WireMockExtension.newInstance()
                .options(wireMockConfig().dynamicPort())
                .build();
        private String participantTokenJwt;
        private ECKey oauthServerSigningKey;

        @BeforeEach
        void setup(ManagementEndToEndTestContext context, ParticipantContextService participantContextService, ParticipantContextConfigStore configStore)
                throws JOSEException {

            createParticipant(participantContextService, configStore, PARTICIPANT_CONTEXT_ID);


            // stub JWKS endpoint at /jwks/ returning 200 OK with a simple JWKS
            oauthServerSigningKey = new ECKeyGenerator(Curve.P_256).keyID(UUID.randomUUID().toString()).generate();
            participantTokenJwt = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey);

            // create JWKS with the participant's key
            var jwks = createObjectBuilder()
                    .add("keys", createArrayBuilder().add(createObjectBuilder(
                            oauthServerSigningKey.toPublicJWK().toJSONObject())))
                    .build()
                    .toString();

            // use wiremock to host a JWKS endpoint
            mockJwksServer.stubFor(any(urlPathEqualTo("/.well-known/jwks"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(jwks)));
        }

        @AfterEach
        void teardown(ParticipantContextService participantContextService) {
            var list = participantContextService.search(QuerySpec.max())
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));

            for (var p : list) {
                participantContextService.deleteParticipantContext(p.getParticipantContextId()).orElseThrow(f -> new AssertionError(f.getFailureDetail()));
            }
        }

        @Test
        void requestCatalog_tokenBearerNotOwner(ManagementEndToEndTestContext context, ParticipantContextService srv, ParticipantContextConfigStore store) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(TYPE, "CatalogRequest")
                    .add("counterPartyAddress", context.providerProtocolUrl(COUNTER_PARTY_ID, "/2025-1"))
                    .add("counterPartyId", COUNTER_PARTY_ID)
                    .add("protocol", "dataspace-protocol-http:2025-1")
                    .build()
                    .toString();

            var otherParticipant = "other-participant";
            createParticipant(srv, store, otherParticipant);
            var token = context.createToken(otherParticipant, oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .body(requestBody)
                    .post("/v4alpha/participants/%s/catalog/request".formatted(PARTICIPANT_CONTEXT_ID))
                    .then()
                    .log().ifValidationFails()
                    .statusCode(403)
                    .body(containsString("User '%s' is not authorized to access this resource".formatted(otherParticipant)));
        }

        @Test
        void requestCatalog_tokenBearerIsAdmin(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(TYPE, "CatalogRequest")
                    .add("counterPartyAddress", context.providerProtocolUrl(COUNTER_PARTY_ID, "/2025-1"))
                    .add("counterPartyId", COUNTER_PARTY_ID)
                    .add("protocol", "dataspace-protocol-http:2025-1")
                    .build()
                    .toString();

            context.baseRequest(context.createAdminToken(oauthServerSigningKey))
                    .contentType(JSON)
                    .body(requestBody)
                    .post("/v4alpha/participants/%s/catalog/request".formatted(PARTICIPANT_CONTEXT_ID))
                    .then()
                    .log().ifValidationFails()
                    .statusCode(200)
                    .contentType(JSON)
                    .body(TYPE, is("Catalog"));
        }

        @Test
        void requestCatalog_tokenLacksScope(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(TYPE, "CatalogRequest")
                    .add("counterPartyAddress", context.providerProtocolUrl(COUNTER_PARTY_ID, "/2025-1"))
                    .add("counterPartyId", COUNTER_PARTY_ID)
                    .add("protocol", "dataspace-protocol-http:2025-1")
                    .build()
                    .toString();

            var offendingToken = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:fizzbuzz"));

            context.baseRequest(offendingToken)
                    .contentType(JSON)
                    .body(requestBody)
                    .post("/v4alpha/participants/%s/catalog/request".formatted(PARTICIPANT_CONTEXT_ID))
                    .then()
                    .log().ifValidationFails()
                    .statusCode(403)
                    .body(matchesRegex("(?s).*Required scope.*management-api:read.*missing.*"));
        }

        @Test
        void requestCatalog_tokenHasWrongRole(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(TYPE, "CatalogRequest")
                    .add("counterPartyAddress", context.providerProtocolUrl(COUNTER_PARTY_ID, "/2025-1"))
                    .add("counterPartyId", COUNTER_PARTY_ID)
                    .add("protocol", "dataspace-protocol-http:2025-1")
                    .build()
                    .toString();

            var offendingToken = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("role", "some-role"));

            context.baseRequest(offendingToken)
                    .contentType(JSON)
                    .body(requestBody)
                    .post("/v4alpha/participants/%s/catalog/request".formatted(PARTICIPANT_CONTEXT_ID))
                    .then()
                    .log().ifValidationFails()
                    .statusCode(403)
                    .body(containsString("Required user role not satisfied"));
        }

        @Test
        void requestCatalog_shouldReturnCatalog_withoutQuerySpec(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(TYPE, "CatalogRequest")
                    .add("counterPartyAddress", context.providerProtocolUrl(COUNTER_PARTY_ID, "/2025-1"))
                    .add("counterPartyId", COUNTER_PARTY_ID)
                    .add("protocol", "dataspace-protocol-http:2025-1")
                    .build()
                    .toString();

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .body(requestBody)
                    .post("/v4alpha/participants/%s/catalog/request".formatted(PARTICIPANT_CONTEXT_ID))
                    .then()
                    .log().ifValidationFails()
                    .statusCode(200)
                    .contentType(JSON)
                    .body(TYPE, is("Catalog"));
        }

        @Test
        void requestCatalog_shouldReturnCatalog_withQuerySpec(ManagementEndToEndTestContext context, AssetIndex assetIndex,
                                                              PolicyDefinitionStore policyDefinitionStore,
                                                              ContractDefinitionStore contractDefinitionStore) {

            assetIndex.create(createAsset("id-1", "test-type").build());
            assetIndex.create(createAsset("id-2", "test-type").build());
            createContractOffer(policyDefinitionStore, contractDefinitionStore, List.of());

            var criteria = createArrayBuilder()
                    .add(createObjectBuilder()
                            .add(TYPE, "Criterion")
                            .add("operandLeft", EDC_NAMESPACE + "id")
                            .add("operator", "=")
                            .add("operandRight", "id-2")
                            .build()
                    )
                    .build();

            var querySpec = createObjectBuilder()
                    .add(TYPE, "QuerySpec")
                    .add("filterExpression", criteria)
                    .add("limit", 1);

            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(TYPE, "CatalogRequest")
                    .add("counterPartyAddress", context.providerProtocolUrl(COUNTER_PARTY_ID, "/2025-1"))
                    .add("counterPartyId", COUNTER_PARTY_ID)
                    .add("protocol", "dataspace-protocol-http:2025-1")
                    .add("querySpec", querySpec)
                    .build()
                    .toString();

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .body(requestBody)
                    .post("/v4alpha/participants/%s/catalog/request".formatted(PARTICIPANT_CONTEXT_ID))
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .body(TYPE, is("Catalog"))
                    .body("dataset[0].id", is("id-2"));
        }

        @Test
        void requestCatalog_shouldReturnBadRequest_withMissingFields(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(TYPE, "CatalogRequest")
                    .build()
                    .toString();

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .body(requestBody)
                    .post("/v4alpha/participants/%s/catalog/request".formatted(PARTICIPANT_CONTEXT_ID))
                    .then()
                    .log().ifValidationFails()
                    .statusCode(400)
                    .contentType(JSON)
                    .body("[0].message", containsString("required property 'protocol' not found"))
                    .body("[1].message", containsString("required property 'counterPartyAddress' not found"))
                    .body("[2].message", containsString("required property 'counterPartyId' not found"));
        }

        @Test
        void requestCatalog_whenAssetIsCatalogAsset_shouldReturnCatalogOfCatalogs(ManagementEndToEndTestContext context, AssetIndex assetIndex,
                                                                                  PolicyDefinitionStore policyDefinitionStore,
                                                                                  ContractDefinitionStore contractDefinitionStore) {

            // create CatalogAsset
            var catalogAssetId = "catalog-asset-" + UUID.randomUUID();
            var httpData = createAsset(catalogAssetId, "HttpData")
                    .property(Asset.PROPERTY_IS_CATALOG, true)
                    .participantContextId(COUNTER_PARTY_ID)
                    .build();
            httpData.getDataAddress().getProperties().put(EDC_NAMESPACE + "baseUrl", "http://quizzqua.zz/buzz");
            assetIndex.create(httpData);

            // create conventional asset
            var normalAssetId = "normal-asset-" + UUID.randomUUID();
            assetIndex.create(createAsset(normalAssetId, "test-type").participantContextId(COUNTER_PARTY_ID).build());

            var assetSelectorCriteria = List.of(Criterion.criterion("id", "in", List.of(catalogAssetId, normalAssetId)));

            createContractOffer(policyDefinitionStore, contractDefinitionStore, assetSelectorCriteria);

            // request all assets
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(TYPE, "CatalogRequest")
                    .add("counterPartyAddress", context.providerProtocolUrl(COUNTER_PARTY_ID, "/2025-1"))
                    .add("counterPartyId", COUNTER_PARTY_ID)
                    .add("protocol", "dataspace-protocol-http:2025-1")
                    .build()
                    .toString();

            var body = context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .body(requestBody)
                    .post("/v4alpha/participants/%s/catalog/request".formatted(PARTICIPANT_CONTEXT_ID))
                    .then()
                    .statusCode(200)
                    .contentType(JSON);

            var str = body.extract().asString();
            body.body(TYPE, is("Catalog"))
                    .body("'service'", notNullValue())
                    // findAll is the restAssured way to express JSON Path filters
                    .body("catalog[0].'@type'", equalTo("Catalog"))
                    .body("catalog[0].isCatalog", equalTo(true))
                    .body("catalog[0].'@id'", equalTo(catalogAssetId))
                    .body("catalog[0].service[0].endpointURL", equalTo("http://quizzqua.zz/buzz"))
                    .body("catalog[0].distribution[0].accessService.'@id'", equalTo(Base64.getUrlEncoder().encodeToString(catalogAssetId.getBytes())));
        }

        @Test
        void getDataset_shouldReturnDataset(ManagementEndToEndTestContext context, AssetIndex assetIndex,
                                            PolicyDefinitionStore policyDefinitionStore,
                                            ContractDefinitionStore contractDefinitionStore,
                                            DataPlaneInstanceStore dataPlaneInstanceStore) {
            var dataPlaneInstance = DataPlaneInstance.Builder.newInstance().url("http://localhost/any")
                    .allowedSourceType("test-type").allowedTransferType("any-PULL").build();
            dataPlaneInstanceStore.save(dataPlaneInstance);

            createContractOffer(policyDefinitionStore, contractDefinitionStore, List.of());
            assetIndex.create(createAsset("asset-id", "test-type").build());
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(TYPE, "DatasetRequest")
                    .add(ID, "asset-id")
                    .add("counterPartyAddress", context.providerProtocolUrl(COUNTER_PARTY_ID, "/2025-1"))
                    .add("counterPartyId", COUNTER_PARTY_ID)
                    .add("protocol", "dataspace-protocol-http:2025-1")
                    .build()
                    .toString();

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .body(requestBody)
                    .post("/v4alpha/participants/%s/catalog/dataset/request".formatted(PARTICIPANT_CONTEXT_ID))
                    .then()
                    .log().ifValidationFails()
                    .statusCode(200)
                    .contentType(JSON)
                    .body(ID, is("asset-id"))
                    .body(TYPE, is("Dataset"))
                    .body("distribution[0].accessService.'@id'", notNullValue());
        }

        @Test
        void getDatasetWithResponseChannel_shouldReturnDataset(ManagementEndToEndTestContext context, AssetIndex assetIndex,
                                                               DataPlaneInstanceStore dataPlaneInstanceStore,
                                                               PolicyDefinitionStore policyDefinitionStore,
                                                               ContractDefinitionStore contractDefinitionStore) {

            createContractOffer(policyDefinitionStore, contractDefinitionStore, List.of());

            var dataPlaneInstance = DataPlaneInstance.Builder.newInstance().url("http://localhost/any")
                    .allowedDestType("any").allowedSourceType("test-type")
                    .allowedTransferType("any-PULL").allowedTransferType("any-PULL-response").build();
            dataPlaneInstanceStore.save(dataPlaneInstance);

            var responseChannel = DataAddress.Builder.newInstance()
                    .type("response")
                    .build();

            var dataAddressWithResponseChannel = DataAddress.Builder.newInstance()
                    .type("test-type")
                    .responseChannel(responseChannel)
                    .build();
            assetIndex.create(createAsset("asset-response", dataAddressWithResponseChannel).build());
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(TYPE, "DatasetRequest")
                    .add(ID, "asset-response")
                    .add("counterPartyAddress", context.providerProtocolUrl(COUNTER_PARTY_ID, "/2025-1"))
                    .add("counterPartyId", COUNTER_PARTY_ID)
                    .add("protocol", "dataspace-protocol-http:2025-1")
                    .build()
                    .toString();

            context.baseRequest(participantTokenJwt)
                    .contentType(JSON)
                    .body(requestBody)
                    .post("/v4alpha/participants/%s/catalog/dataset/request".formatted(PARTICIPANT_CONTEXT_ID))
                    .then()
                    .log().ifValidationFails()
                    .statusCode(200)
                    .contentType(JSON)
                    .body(ID, is("asset-response"))
                    .body(TYPE, is("Dataset"))
                    .body("distribution[0].format", is("any-PULL-response"));
        }

        @Test
        void getDataset_tokenBearerNotOwner(ManagementEndToEndTestContext context, ParticipantContextService srv, ParticipantContextConfigStore store) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(TYPE, "DatasetRequest")
                    .add(ID, "asset-id")
                    .add("counterPartyAddress", context.providerProtocolUrl(COUNTER_PARTY_ID, "/2025-1"))
                    .add("counterPartyId", COUNTER_PARTY_ID)
                    .add("protocol", "dataspace-protocol-http:2025-1")
                    .build()
                    .toString();


            var otherParticipant = "other-participant";
            createParticipant(srv, store, otherParticipant);
            var token = context.createToken(otherParticipant, oauthServerSigningKey);

            context.baseRequest(token)
                    .contentType(JSON)
                    .body(requestBody)
                    .post("/v4alpha/participants/%s/catalog/dataset/request".formatted(PARTICIPANT_CONTEXT_ID))
                    .then()
                    .log().ifValidationFails()
                    .statusCode(403)
                    .body(containsString("User '%s' is not authorized to access this resource".formatted(otherParticipant)));
        }

        @Test
        void getDataset_tokenBearerIsAdmin(ManagementEndToEndTestContext context, DataPlaneInstanceStore dataPlaneInstanceStore,
                                           PolicyDefinitionStore policyDefinitionStore, ContractDefinitionStore contractDefinitionStore,
                                           AssetIndex assetIndex) {
            var dataPlaneInstance = DataPlaneInstance.Builder.newInstance().url("http://localhost/any")
                    .allowedSourceType("test-type").allowedTransferType("any-PULL").build();
            dataPlaneInstanceStore.save(dataPlaneInstance);

            createContractOffer(policyDefinitionStore, contractDefinitionStore, List.of());
            assetIndex.create(createAsset("asset-id", "test-type").build());
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(TYPE, "DatasetRequest")
                    .add(ID, "asset-id")
                    .add("counterPartyAddress", context.providerProtocolUrl(COUNTER_PARTY_ID, "/2025-1"))
                    .add("counterPartyId", COUNTER_PARTY_ID)
                    .add("protocol", "dataspace-protocol-http:2025-1")
                    .build()
                    .toString();

            context.baseRequest(context.createAdminToken(oauthServerSigningKey))
                    .contentType(JSON)
                    .body(requestBody)
                    .post("/v4alpha/participants/%s/catalog/dataset/request".formatted(PARTICIPANT_CONTEXT_ID))
                    .then()
                    .log().ifValidationFails()
                    .statusCode(200)
                    .contentType(JSON)
                    .body(ID, is("asset-id"))
                    .body(TYPE, is("Dataset"))
                    .body("distribution[0].accessService.'@id'", notNullValue());
        }

        @Test
        void getDataset_tokenLacksScope(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(TYPE, "DatasetRequest")
                    .add(ID, "asset-id")
                    .add("counterPartyAddress", context.providerProtocolUrl(COUNTER_PARTY_ID, "/2025-1"))
                    .add("counterPartyId", COUNTER_PARTY_ID)
                    .add("protocol", "dataspace-protocol-http:2025-1")
                    .build()
                    .toString();

            var offendingToken = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("scope", "management-api:fizzbuzz"));

            context.baseRequest(offendingToken)
                    .contentType(JSON)
                    .body(requestBody)
                    .post("/v4alpha/participants/%s/catalog/dataset/request".formatted(PARTICIPANT_CONTEXT_ID))
                    .then()
                    .log().ifValidationFails()
                    .statusCode(403)
                    .body(matchesRegex("(?s).*Required scope.*management-api:read.*missing.*"));
        }

        @Test
        void getDataset_tokenHasWrongRole(ManagementEndToEndTestContext context) {
            var requestBody = createObjectBuilder()
                    .add(CONTEXT, createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2))
                    .add(TYPE, "DatasetRequest")
                    .add(ID, "asset-id")
                    .add("counterPartyAddress", context.providerProtocolUrl(COUNTER_PARTY_ID, "/2025-1"))
                    .add("counterPartyId", COUNTER_PARTY_ID)
                    .add("protocol", "dataspace-protocol-http:2025-1")
                    .build()
                    .toString();

            var offendingToken = context.createToken(PARTICIPANT_CONTEXT_ID, oauthServerSigningKey, Map.of("role", "some-role"));

            context.baseRequest(offendingToken)
                    .contentType(JSON)
                    .body(requestBody)
                    .post("/v4alpha/participants/%s/catalog/dataset/request".formatted(PARTICIPANT_CONTEXT_ID))
                    .then()
                    .log().ifValidationFails()
                    .statusCode(403)
                    .body(containsString("Required user role not satisfied"));
        }

        private void createContractOffer(PolicyDefinitionStore policyStore, ContractDefinitionStore contractDefStore, List<Criterion> assetsSelectorCritera) {

            var policyId = UUID.randomUUID().toString();

            var policy = Policy.Builder.newInstance()
                    .build();

            var contractDefinition = ContractDefinition.Builder.newInstance()
                    .id(UUID.randomUUID().toString())
                    .contractPolicyId(policyId)
                    .accessPolicyId(policyId)
                    .assetsSelector(assetsSelectorCritera)
                    .participantContextId(PARTICIPANT_CONTEXT_ID)
                    .build();


            policyStore.create(PolicyDefinition.Builder.newInstance().id(policyId)
                    .participantContextId(PARTICIPANT_CONTEXT_ID)
                    .policy(policy).build());
            contractDefStore.save(contractDefinition);

        }

        private Asset.Builder createAsset(String id, String sourceType) {
            var address = DataAddress.Builder.newInstance()
                    .type(sourceType)
                    .build();
            return createAsset(id, address);
        }

        private Asset.Builder createAsset(String id, DataAddress address) {
            return Asset.Builder.newInstance()
                    .dataAddress(address)
                    .participantContextId(PARTICIPANT_CONTEXT_ID)
                    .id(id);
        }


        private void createParticipant(ParticipantContextService participantContextService,
                                       ParticipantContextConfigStore configStore, String participantContextId) {
            var pc = ParticipantContext.Builder.newInstance()
                    .participantContextId(participantContextId)
                    .state(ParticipantContextState.ACTIVATED)
                    .build();

            var config = ParticipantContextConfiguration.Builder.newInstance()
                    .participantContextId(participantContextId)
                    .entries(Map.of("edc.mock.region", "eu",
                            "edc.participant.id", "did:web:" + PARTICIPANT_CONTEXT_ID
                    ))
                    .build();

            configStore.save(config);

            participantContextService.createParticipantContext(pc)
                    .orElseThrow(f -> new AssertionError(f.getFailureDetail()));
        }
    }

    @Nested
    @EndToEndTest
    class InMemory extends Tests {

        @Order(1)
        @RegisterExtension
        static RuntimeExtension runtime = ComponentRuntimeExtension.Builder.newInstance()
                .name(Runtimes.ControlPlane.NAME)
                .modules(Runtimes.ControlPlane.MODULES)
                .endpoints(Runtimes.ControlPlane.ENDPOINTS.build())
                .configurationProvider(Runtimes.ControlPlane::config)
                .configurationProvider(() -> ConfigFactory.fromMap(Map.of("edc.iam.oauth2.jwks.url",
                        "http://localhost:" + mockJwksServer.getPort() + "/.well-known/jwks")))
                .paramProvider(ManagementEndToEndTestContext.class,
                        ManagementEndToEndTestContext::forContext)
                .build();
    }

    @Nested
    @PostgresqlIntegrationTest
    class Postgres extends Tests {

        @RegisterExtension
        @Order(0)
        static final PostgresqlEndToEndExtension POSTGRES_EXTENSION = new PostgresqlEndToEndExtension(
                createPgContainer());

        @Order(0)
        @RegisterExtension
        static final NatsEndToEndExtension NATS_EXTENSION = new NatsEndToEndExtension();

        @Order(1)
        @RegisterExtension
        static final BeforeAllCallback SETUP = context -> {
            POSTGRES_EXTENSION.createDatabase(Runtimes.ControlPlane.NAME.toLowerCase());
            NATS_EXTENSION.createStream("state_machine", "negotiations.>", "transfers.>");
            NATS_EXTENSION.createConsumer("state_machine", "cn-subscriber", "negotiations.>");
            NATS_EXTENSION.createConsumer("state_machine", "tp-subscriber", "transfers.>");
        };
        @Order(3)
        @RegisterExtension
        static final BeforeAllCallback SEED = context -> {
            POSTGRES_EXTENSION.execute(Runtimes.ControlPlane.NAME.toLowerCase(),
                    "ALTER TABLE edc_contract_negotiation REPLICA IDENTITY FULL;");
            POSTGRES_EXTENSION.execute(Runtimes.ControlPlane.NAME.toLowerCase(),
                    "ALTER TABLE edc_transfer_process REPLICA IDENTITY FULL;");
        };
        @Order(2)
        @RegisterExtension
        static RuntimeExtension runtime = ComponentRuntimeExtension.Builder.newInstance()
                .name(Runtimes.ControlPlane.NAME)
                .modules(Runtimes.ControlPlane.MODULES)
                .modules(Runtimes.ControlPlane.PG_MODULES)
                .endpoints(Runtimes.ControlPlane.ENDPOINTS.build())
                .configurationProvider(Runtimes.ControlPlane::config)
                .configurationProvider(() -> POSTGRES_EXTENSION
                        .configFor(Runtimes.ControlPlane.NAME.toLowerCase()))
                .configurationProvider(
                        CatalogApiV4EndToEndTest.Postgres::runtimeConfiguration)
                .configurationProvider(() -> ConfigFactory.fromMap(Map.of("edc.iam.oauth2.jwks.url",
                        "http://localhost:" + mockJwksServer.getPort() + "/.well-known/jwks")))
                .paramProvider(ManagementEndToEndTestContext.class,
                        ManagementEndToEndTestContext::forContext)
                .build();

        private static Config runtimeConfiguration() {
            return ConfigFactory.fromMap(new HashMap<>() {
                {
                    put("edc.postgres.cdc.url", POSTGRES_EXTENSION
                            .getJdbcUrl(Runtimes.ControlPlane.NAME.toLowerCase()));
                    put("edc.postgres.cdc.user", POSTGRES_EXTENSION.getUsername());
                    put("edc.postgres.cdc.password", POSTGRES_EXTENSION.getPassword());
                    put("edc.postgres.cdc.slot",
                            "edc_cdc_slot_" + Runtimes.ControlPlane.NAME.toLowerCase());
                    put("edc.nats.cn.subscriber.url", NATS_EXTENSION.getNatsUrl());
                    put("edc.nats.cn.publisher.url", NATS_EXTENSION.getNatsUrl());
                    put("edc.nats.tp.subscriber.url", NATS_EXTENSION.getNatsUrl());
                    put("edc.nats.tp.publisher.url", NATS_EXTENSION.getNatsUrl());
                }
            });
        }

    }

}