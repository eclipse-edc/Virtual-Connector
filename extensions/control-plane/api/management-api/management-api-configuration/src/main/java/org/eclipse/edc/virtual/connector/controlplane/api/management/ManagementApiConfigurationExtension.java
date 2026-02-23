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

package org.eclipse.edc.virtual.connector.controlplane.api.management;

import jakarta.json.Json;
import org.eclipse.edc.api.auth.spi.AuthorizationService;
import org.eclipse.edc.api.transformer.JsonObjectFromCallbackAddressTransformer;
import org.eclipse.edc.api.transformer.JsonObjectFromIdResponseTransformer;
import org.eclipse.edc.api.transformer.JsonObjectToCallbackAddressTransformer;
import org.eclipse.edc.connector.controlplane.transform.edc.from.JsonObjectFromAssetTransformer;
import org.eclipse.edc.connector.controlplane.transform.edc.from.JsonObjectFromDataplaneMetadataTransformer;
import org.eclipse.edc.connector.controlplane.transform.edc.to.JsonObjectToAssetTransformer;
import org.eclipse.edc.connector.controlplane.transform.edc.to.JsonObjectToDataplaneMetadataTransformer;
import org.eclipse.edc.connector.controlplane.transform.odrl.OdrlTransformersFactory;
import org.eclipse.edc.connector.controlplane.transform.odrl.from.JsonObjectFromPolicyTransformer;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.participantcontext.spi.types.ParticipantResource;
import org.eclipse.edc.runtime.metamodel.annotation.Configuration;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.Settings;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.transform.transformer.edc.from.JsonObjectFromCriterionTransformer;
import org.eclipse.edc.transform.transformer.edc.from.JsonObjectFromDataAddressTransformer;
import org.eclipse.edc.transform.transformer.edc.from.JsonObjectFromQuerySpecTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonObjectToCriterionTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonObjectToDataAddressTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonObjectToQuerySpecTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonValueToGenericTypeTransformer;
import org.eclipse.edc.web.jersey.providers.jsonld.ObjectMapperProvider;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import static java.lang.String.format;
import static org.eclipse.edc.api.management.ManagementApi.MANAGEMENT_API_CONTEXT;
import static org.eclipse.edc.api.management.ManagementApi.MANAGEMENT_API_V_4;
import static org.eclipse.edc.api.management.ManagementApi.MANAGEMENT_SCOPE;
import static org.eclipse.edc.api.management.ManagementApi.MANAGEMENT_SCOPE_V4;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_PREFIX;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_PREFIX;
import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;
import static org.eclipse.virtualized.api.management.VirtualManagementApi.EDC_V_CONNECTOR_MANAGEMENT_CONTEXT_V2;

@Extension(value = ManagementApiConfigurationExtension.NAME)
public class ManagementApiConfigurationExtension implements ServiceExtension {

    public static final String NAME = "Management API: Configuration";

    @Inject
    private JsonLd jsonLd;

    @Inject
    private TypeManager typeManager;
    @Configuration
    private ManagementApiConfiguration apiConfiguration;
    @Inject
    private PortMappingRegistry portMappingRegistry;

    @Inject
    private AuthorizationService authorizationService;
    @Inject
    private ParticipantContextService participantContextService;
    @Inject
    private WebService webService;
    @Inject
    private TypeTransformerRegistry transformerRegistry;
    @Inject
    private ParticipantIdMapper participantIdMapper;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {

        var portMapping = new PortMapping(ApiContext.MANAGEMENT, apiConfiguration.port(), apiConfiguration.path());
        portMappingRegistry.register(portMapping);

        jsonLd.registerNamespace(VOCAB, EDC_NAMESPACE, MANAGEMENT_SCOPE);
        jsonLd.registerNamespace(EDC_PREFIX, EDC_NAMESPACE, MANAGEMENT_SCOPE);
        jsonLd.registerNamespace(ODRL_PREFIX, ODRL_SCHEMA, MANAGEMENT_SCOPE);

        jsonLd.registerContext(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2, MANAGEMENT_SCOPE_V4);

        getResourceUri("document/v-management-context-v2.jsonld")
                .onSuccess(uri -> jsonLd.registerCachedDocument(EDC_V_CONNECTOR_MANAGEMENT_CONTEXT_V2, uri))
                .onFailure(error -> context.getMonitor().severe(format("Failed to register management api v4 context: %s", error)));

        webService.registerResource(ApiContext.MANAGEMENT, new ObjectMapperProvider(typeManager, JSON_LD));

        var managementApiTransformerRegistry = transformerRegistry.forContext(MANAGEMENT_API_CONTEXT);

        var factory = Json.createBuilderFactory(Map.of());
        managementApiTransformerRegistry.register(new JsonObjectFromDataAddressTransformer(factory, typeManager, JSON_LD));
        managementApiTransformerRegistry.register(new JsonObjectFromAssetTransformer(factory, typeManager, JSON_LD));
        managementApiTransformerRegistry.register(new JsonObjectFromPolicyTransformer(factory, participantIdMapper));
        managementApiTransformerRegistry.register(new JsonObjectFromQuerySpecTransformer(factory));
        managementApiTransformerRegistry.register(new JsonObjectFromCriterionTransformer(factory, typeManager, JSON_LD));
        managementApiTransformerRegistry.register(new JsonObjectFromCallbackAddressTransformer(factory));
        managementApiTransformerRegistry.register(new JsonObjectFromIdResponseTransformer(factory));
        managementApiTransformerRegistry.register(new JsonObjectFromDataplaneMetadataTransformer(factory, () -> typeManager.getMapper(JSON_LD)));

        OdrlTransformersFactory.jsonObjectToOdrlTransformers(participantIdMapper).forEach(managementApiTransformerRegistry::register);
        managementApiTransformerRegistry.register(new JsonObjectToDataAddressTransformer());
        managementApiTransformerRegistry.register(new JsonObjectToQuerySpecTransformer());
        managementApiTransformerRegistry.register(new JsonObjectToCriterionTransformer());
        managementApiTransformerRegistry.register(new JsonObjectToAssetTransformer());
        managementApiTransformerRegistry.register(new JsonValueToGenericTypeTransformer(typeManager, JSON_LD));
        managementApiTransformerRegistry.register(new JsonObjectToCallbackAddressTransformer());
        managementApiTransformerRegistry.register(new JsonObjectToDataplaneMetadataTransformer());
        var managementApiTransformerRegistryV4Alpha = managementApiTransformerRegistry.forContext(MANAGEMENT_API_V_4);

        managementApiTransformerRegistryV4Alpha.register(new JsonObjectFromPolicyTransformer(factory, participantIdMapper, new JsonObjectFromPolicyTransformer.TransformerConfig(true, true)));


        authorizationService.addLookupFunction(ParticipantContext.class, this::findParticipant);
    }

    private ParticipantResource findParticipant(String resourceId, String id) {
        if (resourceId.equals(id)) {
            return participantContextService.getParticipantContext(id).orElse(serviceFailure -> null);
        }
        return null;
    }

    @NotNull
    private Result<URI> getResourceUri(String name) {
        var uri = getClass().getClassLoader().getResource(name);
        if (uri == null) {
            return Result.failure(format("Cannot find resource %s", name));
        }

        try {
            return Result.success(uri.toURI());
        } catch (URISyntaxException e) {
            return Result.failure(format("Cannot read resource %s: %s", name, e.getMessage()));
        }
    }

    @Settings
    record ManagementApiConfiguration(
            @Setting(key = "web.http." + ApiContext.MANAGEMENT + ".port", description = "Port for " + ApiContext.MANAGEMENT + " api context", defaultValue = "8081")
            int port,
            @Setting(key = "web.http." + ApiContext.MANAGEMENT + ".path", description = "Path for " + ApiContext.MANAGEMENT + " api context", defaultValue = "/api/mgmt")
            String path
    ) {

    }
}
