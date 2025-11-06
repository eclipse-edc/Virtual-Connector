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

import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.edc.api.auth.spi.ParticipantPrincipal;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.web.spi.exception.AuthenticationFailedException;

import java.security.Principal;
import java.text.ParseException;
import java.util.Map;

import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;

/**
 * A {@link ContainerRequestFilter} that extracts a {@link ParticipantPrincipal} from the Authorization Header, specifically,
 * the JWT that is contained in the Authorization Header.
 */
@PreMatching
@Priority(Priorities.AUTHENTICATION)
public class ServicePrincipalAuthenticationFilter implements ContainerRequestFilter {

    private final ParticipantContextService participantContextService;

    public ServicePrincipalAuthenticationFilter(ParticipantContextService participantContextService) {
        this.participantContextService = participantContextService;
    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext) {
        var authHeaders = containerRequestContext.getHeaders().get(AUTHORIZATION);

        // reject 0 or >1 api key headers
        if (authHeaders == null || authHeaders.size() != 1) {
            containerRequestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED.getStatusCode(), "Authorization Header missing").build());
        } else {
            var authHeader = authHeaders.get(0);
            if (!authHeader.startsWith("Bearer ")) {
                containerRequestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED.getStatusCode(), "Authorization Header invalid: expected 'Bearer' prefix").build());
                return;
            }
            authHeader = authHeader.replace("Bearer ", "");

            var claims = parseJwt(authHeader);
            var participantContextId = claims.get("participant_context_id");

            String participantContextIdString = null;
            if (participantContextId != null) {
                if (participantContextService.getParticipantContext(participantContextId.toString()).failed()) {
                    containerRequestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED.getStatusCode(), "Authorization Header invalid: participant context not found").build());
                    return;
                }
                participantContextIdString = participantContextId.toString();
            }


            var servicePrincipal = new ParticipantPrincipal(participantContextIdString,
                    claims.get("role").toString(),
                    claims.get("scope").toString());
            containerRequestContext.setSecurityContext(new SecurityContext() {
                @Override
                public Principal getUserPrincipal() {
                    return servicePrincipal;
                }

                @Override
                public boolean isUserInRole(String s) {
                    return servicePrincipal.getRoles().contains(s);
                }

                @Override
                public boolean isSecure() {
                    return containerRequestContext.getUriInfo().getBaseUri().toString().startsWith("https");
                }

                @Override
                public String getAuthenticationScheme() {
                    return null;
                }
            });
        }
    }

    private Map<String, Object> parseJwt(String jwt) {
        try {
            return SignedJWT.parse(jwt).getJWTClaimsSet().toJSONObject();
        } catch (ParseException e) {
            throw new AuthenticationFailedException(e.getMessage());
        }
    }

}
