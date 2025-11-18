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

package org.eclipse.edc.virtualized.connector.controlplane.api.management.participantcontext.v4;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.edc.api.management.schema.ManagementApiJsonSchema;

@OpenAPIDefinition(info = @Info(title = "ParticipantContext Management API", version = "v4alpha"))
@Tag(name = "Participant Context v4alpha")
public interface ParticipantContextApiV4 {

    @Operation(description = "Creates a new ParticipantContext object.",
            requestBody = @RequestBody(content = @Content(schema = @Schema(ref = ManagementApiJsonSchema.V4.PARTICIPANT_CONTEXT), mediaType = "application/json")),
            responses = {
                    @ApiResponse(responseCode = "201", description = "The ParticipantContext was created successfully, its API token is returned in the response body.",
                            content = @Content(schema = @Schema(ref = ManagementApiJsonSchema.V4.ID_RESPONSE))),
                    @ApiResponse(responseCode = "400", description = "Request body was malformed, or the request could not be processed",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json")),
                    @ApiResponse(responseCode = "401", description = "The request could not be completed, because either the authentication was missing or was not valid.",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json")),
                    @ApiResponse(responseCode = "409", description = "Can't create the ParticipantContext, because a object with the same ID already exists",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json"))
            }
    )
    JsonObject createParticipantV4(JsonObject participantContext);

    @Operation(description = "Gets ParticipantContexts by ID.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The list of ParticipantContexts.",
                            content = @Content(schema = @Schema(ref = ManagementApiJsonSchema.V4.PARTICIPANT_CONTEXT))),
                    @ApiResponse(responseCode = "400", description = "Request body was malformed, or the request could not be processed",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json")),
                    @ApiResponse(responseCode = "401", description = "The request could not be completed, because either the authentication was missing or was not valid.",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json")),
                    @ApiResponse(responseCode = "404", description = "A ParticipantContext with the given ID does not exist.",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json"))
            }
    )
    JsonObject getParticipantV4(String participantContextId, SecurityContext securityContext);

    @Operation(description = "Updates a ParticipantContext object.",
            requestBody = @RequestBody(content = @Content(schema = @Schema(ref = ManagementApiJsonSchema.V4.PARTICIPANT_CONTEXT), mediaType = "application/json")),
            responses = {
                    @ApiResponse(responseCode = "204", description = "The ParticipantContext was updated successfully."),
                    @ApiResponse(responseCode = "400", description = "Request body was malformed, or the request could not be processed",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json")),
                    @ApiResponse(responseCode = "401", description = "The request could not be completed, because either the authentication was missing or was not valid.",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json")),
                    @ApiResponse(responseCode = "409", description = "Can't create the ParticipantContext, because a object with the same ID already exists",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json"))
            }
    )
    void updateParticipantV4(String id, JsonObject manifest);

    @Operation(description = "Delete a ParticipantContext.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "The ParticipantContext was deleted successfully"),
                    @ApiResponse(responseCode = "400", description = "Request body was malformed, or the request could not be processed",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json")),
                    @ApiResponse(responseCode = "401", description = "The request could not be completed, because either the authentication was missing or was not valid.",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json")),
                    @ApiResponse(responseCode = "404", description = "A ParticipantContext with the given ID does not exist.",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json"))
            }
    )
    void deleteParticipantV4(String participantContextId);

    @Operation(description = "Get all DID documents across all Participant Contexts. Requires elevated access.",
            parameters = {
                    @Parameter(name = "offset", description = "the paging offset. defaults to 0"),
                    @Parameter(name = "limit", description = "the page size. defaults to 50")},
            responses = {
                    @ApiResponse(responseCode = "200", description = "The list of ParticipantContexts.",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.PARTICIPANT_CONTEXT)))),
                    @ApiResponse(responseCode = "401", description = "The request could not be completed, because either the authentication was missing or was not valid.",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json")),
                    @ApiResponse(responseCode = "400", description = "The query was malformed or was not understood by the server.",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json")),
            }
    )
    JsonArray getAllParticipantsV4(Integer offset, Integer limit);
}
