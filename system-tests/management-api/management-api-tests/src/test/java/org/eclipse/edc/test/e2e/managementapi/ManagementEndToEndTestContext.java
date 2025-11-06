/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.edc.test.e2e.managementapi;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.specification.RequestSpecification;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.api.auth.spi.ParticipantPrincipal;
import org.eclipse.edc.junit.extensions.ComponentRuntimeContext;
import org.eclipse.edc.junit.utils.LazySupplier;
import org.eclipse.edc.spi.query.Criterion;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import static com.nimbusds.jose.JWSAlgorithm.ES256;
import static io.restassured.RestAssured.given;
import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static jakarta.json.stream.JsonCollectors.toJsonArray;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.web.spi.configuration.ApiContext.MANAGEMENT;
import static org.eclipse.edc.web.spi.configuration.ApiContext.PROTOCOL;

public record ManagementEndToEndTestContext(LazySupplier<URI> managementApiUri, LazySupplier<URI> protocolApiUri) {

    public static ManagementEndToEndTestContext forContext(ComponentRuntimeContext ctx) {
        return new ManagementEndToEndTestContext(ctx.getEndpoint(MANAGEMENT), ctx.getEndpoint(PROTOCOL));
    }

    public RequestSpecification baseRequest(String accessToken) {
        return given()
                .baseUri(managementApiUri.get().toString())
                .header("Authorization", "Bearer " + accessToken)
                .when();
    }

    public String providerProtocolUrl() {
        return providerProtocolUrl("");
    }

    public String providerProtocolUrl(String versionPath) {
        return protocolApiUri.get() + versionPath;
    }

    public JsonObject query(Criterion... criteria) {
        return query(createObjectBuilder().add(VOCAB, EDC_NAMESPACE).build(), criteria);
    }

    public JsonObject queryV2(Criterion... criteria) {
        return query(createArrayBuilder().add(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2).build(), criteria);

    }

    public String createToken(String participantContextId, ECKey key) {
        try {
            var string = UUID.randomUUID().toString();
            var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder()
                    .subject("test-subject")
                    .issuer("test-issuer")
                    .issueTime(new java.util.Date())
                    .expirationTime(new java.util.Date(System.currentTimeMillis() + 3_600_000))
                    .jwtID(string)
                    .claim("scope", "management-api:read")
                    .claim("role", ParticipantPrincipal.ROLE_PARTICIPANT)
                    .claim("participant_context_id", participantContextId)
                    .build();
            var hdr = new JWSHeader.Builder(ES256).keyID(key.getKeyID()).build();
            var jwt = new SignedJWT(hdr, claims);
            var signer = new ECDSASigner(key);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String createAdminToken(ECKey key) {
        try {
            var string = UUID.randomUUID().toString();
            var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder()
                    .subject("test-subject")
                    .issuer("test-issuer")
                    .issueTime(new java.util.Date())
                    .expirationTime(new java.util.Date(System.currentTimeMillis() + 3_600_000))
                    .jwtID(string)
                    .claim("scope", "management-api:read")
                    .claim("role", ParticipantPrincipal.ROLE_ADMIN)
                    .build();
            var hdr = new JWSHeader.Builder(ES256).keyID(key.getKeyID()).build();
            var jwt = new SignedJWT(hdr, claims);
            var signer = new ECDSASigner(key);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonObject query(JsonValue ctx, Criterion... criteria) {
        var criteriaJson = Arrays.stream(criteria)
                .map(it -> {
                            JsonValue operandRight;
                            if (it.getOperandRight() instanceof Collection<?> collection) {
                                operandRight = Json.createArrayBuilder(collection).build();
                            } else {
                                operandRight = Json.createValue(it.getOperandRight().toString());
                            }
                            return createObjectBuilder()
                                    .add(TYPE, "Criterion")
                                    .add("operandLeft", it.getOperandLeft().toString())
                                    .add("operator", it.getOperator())
                                    .add("operandRight", operandRight)
                                    .build();
                        }
                ).collect(toJsonArray());

        return createObjectBuilder()
                .add(CONTEXT, ctx)
                .add(TYPE, "QuerySpec")
                .add("filterExpression", criteriaJson)
                .build();
    }

}
