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

package org.eclipse.edc.identityhub.api.authorization.filter;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.edc.api.auth.spi.ParticipantPrincipal;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.web.spi.exception.AuthenticationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicePrincipalAuthenticationFilterTest {

    private final ParticipantContextService participantContextService = mock();
    private final ServicePrincipalAuthenticationFilter filter = new ServicePrincipalAuthenticationFilter(participantContextService);

    @BeforeEach
    void setup() {
        when(participantContextService.getParticipantContext(anyString()))
                .thenAnswer(i ->
                        ServiceResult.success(ParticipantContext.Builder.newInstance()
                                .participantContextId(i.getArgument(0))
                                .build()));
    }

    @Test
    void filter_success() {
        var request = mock(ContainerRequestContext.class);

        when(request.getHeaders()).thenReturn(headers(Map.of("Authorization", "Bearer " + createJwt())));

        filter.filter(request);

        verify(request).setSecurityContext(argThat(sc -> sc.getUserPrincipal() instanceof ParticipantPrincipal));
    }

    @Test
    void filter_noAuthHeader() {
        var request = mock(ContainerRequestContext.class);
        when(request.getHeaders()).thenReturn(headers(Map.of()));

        filter.filter(request);

        verify(request).abortWith(argThat(response -> response.getStatus() == 401));
    }

    @Test
    void filter_noBearerPrefix() {
        var request = mock(ContainerRequestContext.class);
        when(request.getHeaders()).thenReturn(headers(Map.of("Authorization", createJwt())));

        filter.filter(request);

        verify(request).abortWith(argThat(response -> response.getStatus() == 401));
    }

    @Test
    void filter_tokenNotJwt() {
        var request = mock(ContainerRequestContext.class);
        when(request.getHeaders()).thenReturn(headers(Map.of("Authorization", "Bearer not-a-jwt")));

        assertThatThrownBy(() -> filter.filter(request)).isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void filter_tooManyAuthHeader() {
        var request = mock(ContainerRequestContext.class);

        var headers = new MultivaluedHashMap<String, String>();
        headers.put("Authorization", List.of("key1", "key2"));
        when(request.getHeaders()).thenReturn(headers);

        filter.filter(request);
        verify(request).abortWith(argThat(response -> response.getStatus() == 401));
    }

    @Test
    void filter_userNotResolved() {
        when(participantContextService.getParticipantContext(anyString())).thenReturn(ServiceResult.notFound("test message"));
        var request = mock(ContainerRequestContext.class);

        when(request.getHeaders()).thenReturn(headers(Map.of("Authorization", "Bearer " + createJwt())));
        filter.filter(request);

        verify(request).abortWith(argThat(response -> response.getStatus() == 401));
    }

    private String createJwt() {
        try {
            var kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();

            var claims = new JWTClaimsSet.Builder()
                    .issuer("test-issuer")
                    .subject("test-subject")
                    .claim("scope", "management-api:read")
                    .claim("role", "edcv-participant")
                    .claim("participant_context_id", "test-context-id")
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                    .build();

            var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build();
            var jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(kp.getPrivate()));
            return jwt.serialize();
        } catch (JOSEException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private MultivaluedMap<String, String> headers(Map<String, String> headers) {
        return new MultivaluedHashMap<>(headers);
    }
}