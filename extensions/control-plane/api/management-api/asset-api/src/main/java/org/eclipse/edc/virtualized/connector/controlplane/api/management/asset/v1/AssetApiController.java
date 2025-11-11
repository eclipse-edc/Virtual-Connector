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

package org.eclipse.edc.virtualized.connector.controlplane.api.management.asset.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.edc.api.auth.spi.AuthorizationService;
import org.eclipse.edc.api.auth.spi.ParticipantPrincipal;
import org.eclipse.edc.api.auth.spi.RequiredScope;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.services.spi.asset.AssetService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.spi.validation.SchemaType;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset.EDC_ASSET_TYPE_TERM;
import static org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset.EDC_CATALOG_ASSET_TYPE_TERM;
import static org.eclipse.edc.web.spi.exception.ServiceResultHandler.exceptionMapper;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path("/v4alpha/participants/{participantContextId}/assets")
public class AssetApiController {
    private final TypeTransformerRegistry managementTypeTransformerRegistry;
    private final AssetService assetService;
    private final JsonObjectValidatorRegistry validator;
    private final Monitor monitor;
    private final AuthorizationService authorizationService;

    public AssetApiController(AssetService assetService, TypeTransformerRegistry managementTypeTransformerRegistry, JsonObjectValidatorRegistry validator, Monitor monitor, AuthorizationService authorizationService) {
        this.assetService = assetService;
        this.managementTypeTransformerRegistry = managementTypeTransformerRegistry;
        this.validator = validator;
        this.monitor = monitor;
        this.authorizationService = authorizationService;
    }

    @POST
    @RolesAllowed({ ParticipantPrincipal.ROLE_ADMIN, ParticipantPrincipal.ROLE_PARTICIPANT })
    @RequiredScope("management-api:write")
    public JsonObject createAsset(@PathParam("participantContextId") String participantContextId,
                                  @SchemaType({ EDC_ASSET_TYPE_TERM, EDC_CATALOG_ASSET_TYPE_TERM }) Asset assetJson,
                                  @Context SecurityContext securityContext) {

        authorizationService.authorize(securityContext, participantContextId, participantContextId, ParticipantContext.class)
                .orElseThrow(exceptionMapper(ParticipantContext.class, participantContextId));

        return Json.createObjectBuilder().build();
    }


    @PUT
    @RolesAllowed({ ParticipantPrincipal.ROLE_ADMIN, ParticipantPrincipal.ROLE_PARTICIPANT })
    @RequiredScope("management-api:write")
    public JsonObject updateAsset(@PathParam("participantContextId") String participantContextId,
                                  @SchemaType({ EDC_ASSET_TYPE_TERM, EDC_CATALOG_ASSET_TYPE_TERM }) Asset assetJson,
                                  @Context SecurityContext securityContext) {

        authorizationService.authorize(securityContext, participantContextId, assetJson.getId(), Asset.class)
                .orElseThrow(exceptionMapper(Asset.class, assetJson.getId()));

        return Json.createObjectBuilder().build();
    }

    @GET
    @Path("{id}")
    @RolesAllowed({ ParticipantPrincipal.ROLE_ADMIN, ParticipantPrincipal.ROLE_PARTICIPANT })
    @RequiredScope("management-api:read")
    public JsonObject getAsset(@PathParam("participantContextId") String participantContextId,
                               @PathParam("id") String id,
                               @Context SecurityContext securityContext) {
        var asset = authorizationService.authorize(securityContext, participantContextId, id, Asset.class)
                .orElseThrow(exceptionMapper(Asset.class, id));

        return managementTypeTransformerRegistry.transform(asset, JsonObject.class).orElseThrow(f -> new EdcException(f.getFailureDetail()));
    }
}
