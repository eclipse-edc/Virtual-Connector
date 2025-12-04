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

package org.eclipse.edc.virtual.connector.controlplane.api.management.cel.v4;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.eclipse.edc.api.management.schema.ManagementApiJsonSchema;
import org.eclipse.virtualized.api.management.schema.VirtualManagementApiJsonSchema;

@OpenAPIDefinition(info = @Info(title = "Cel Expressions Management API", version = "v4alpha"))
@Tag(name = "Cel Expressions v4alpha")
public interface CelExpressionApiV4 {

    @Operation(description = "Create a Cel Expression.",
            requestBody = @RequestBody(content = @Content(schema = @Schema(ref = ManagementApiJsonSchema.V4.PARTICIPANT_CONTEXT_CONFIG), mediaType = "application/json")),
            responses = {
                    @ApiResponse(responseCode = "200", description = "The Cel Expression was created successfully"),
                    @ApiResponse(responseCode = "400", description = "Request body was malformed, or the request could not be processed",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json")),
                    @ApiResponse(responseCode = "401", description = "The request could not be completed, because either the authentication was missing or was not valid.",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json")),
                    @ApiResponse(responseCode = "409", description = "Can't create the Cel expression, because a object with the same ID already exists",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json"))
            }
    )
    JsonObject createExpressionV4(JsonObject expression);

    @Operation(description = "Gets an Expression by ID.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The Cel Expression.",
                            content = @Content(schema = @Schema(ref = VirtualManagementApiJsonSchema.V4.CEL_EXPRESSION))),
                    @ApiResponse(responseCode = "400", description = "Request body was malformed, or the request could not be processed",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json")),
                    @ApiResponse(responseCode = "401", description = "The request could not be completed, because either the authentication was missing or was not valid.",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json")),
                    @ApiResponse(responseCode = "404", description = "A Cel Expression with the given ID does not exist.",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json"))
            }
    )
    JsonObject getExpressionV4(String id);

    @Operation(description = "Update an Expression.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "The Cel Expression was updated successfully.",
                            content = @Content(schema = @Schema(ref = VirtualManagementApiJsonSchema.V4.CEL_EXPRESSION))),
                    @ApiResponse(responseCode = "400", description = "Request body was malformed, or the request could not be processed",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json")),
                    @ApiResponse(responseCode = "401", description = "The request could not be completed, because either the authentication was missing or was not valid.",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json")),
                    @ApiResponse(responseCode = "404", description = "A Cel Expression with the given ID does not exist.",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json"))
            }
    )
    void updateExpressionV4(String id, JsonObject expression);

    @Operation(description = "Returns all cel expressions according to a query",
            requestBody = @RequestBody(content = @Content(schema = @Schema(ref = ManagementApiJsonSchema.V4.QUERY_SPEC))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "The cel expressions matching the query",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = VirtualManagementApiJsonSchema.V4.CEL_EXPRESSION)))),
                    @ApiResponse(responseCode = "400", description = "Request was malformed",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR))))}
    )
    JsonArray queryExpressionV4(JsonObject querySpecJson);

    @Operation(description = "Delete an Expression by ID.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "The Cel Expression was deleted.",
                            content = @Content(schema = @Schema(ref = VirtualManagementApiJsonSchema.V4.CEL_EXPRESSION))),
                    @ApiResponse(responseCode = "400", description = "Request body was malformed, or the request could not be processed",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json")),
                    @ApiResponse(responseCode = "401", description = "The request could not be completed, because either the authentication was missing or was not valid.",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json")),
                    @ApiResponse(responseCode = "404", description = "A Cel Expression with the given ID does not exist.",
                            content = @Content(array = @ArraySchema(schema = @Schema(ref = ManagementApiJsonSchema.V4.API_ERROR)), mediaType = "application/json"))
            }
    )
    void deleteExpressionV4(String id);

}
