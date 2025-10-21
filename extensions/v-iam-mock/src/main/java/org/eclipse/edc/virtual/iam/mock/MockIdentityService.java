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

package org.eclipse.edc.virtual.iam.mock;

import org.eclipse.edc.participantcontext.spi.config.ParticipantContextConfig;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.TokenParameters;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.iam.VerificationContext;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.TypeManager;

import static org.eclipse.edc.virtual.iam.mock.IamMockExtension.DEFAULT_FAULTY_CLIENT_ID;
import static org.eclipse.edc.virtual.iam.mock.IamMockExtension.DEFAULT_MOCK_REGION;
import static org.eclipse.edc.virtual.iam.mock.IamMockExtension.EDC_MOCK_FAULTY_CLIENT_ID;
import static org.eclipse.edc.virtual.iam.mock.IamMockExtension.EDC_MOCK_REGION;
import static org.eclipse.edc.virtual.iam.mock.IamMockExtension.PARTICIPANT_ID;

public class MockIdentityService implements IdentityService {
    private final ParticipantContextConfig contextConfig;
    private final TypeManager typeManager;

    public MockIdentityService(ParticipantContextConfig contextConfig, TypeManager typeManager) {
        this.contextConfig = contextConfig;
        this.typeManager = typeManager;
    }

    @Override
    public Result<TokenRepresentation> obtainClientCredentials(String participantContextId, TokenParameters parameters) {
        var token = new MockToken();
        token.setAudience(parameters.getStringClaim("aud"));
        token.setRegion(contextConfig.getString(participantContextId, EDC_MOCK_REGION, DEFAULT_MOCK_REGION));
        token.setClientId(contextConfig.getString(participantContextId, PARTICIPANT_ID));
        TokenRepresentation tokenRepresentation = TokenRepresentation.Builder.newInstance()
                .token(typeManager.writeValueAsString(token))
                .build();
        return Result.success(tokenRepresentation);
    }

    @Override
    public Result<ClaimToken> verifyJwtToken(String participantContextId, TokenRepresentation tokenRepresentation, VerificationContext context) {
        var token = typeManager.readValue(tokenRepresentation.getToken(), MockToken.class);
        var faultyClientId = contextConfig.getString(participantContextId, EDC_MOCK_FAULTY_CLIENT_ID, DEFAULT_FAULTY_CLIENT_ID);
        if (faultyClientId.equals(token.clientId)) {
            return Result.failure("Unauthorized");
        }

        return Result.success(ClaimToken.Builder.newInstance()
                .claim("region", token.region)
                .claim("client_id", token.clientId)
                .build());
    }

    private static class MockToken {
        private String region;
        private String audience;
        private String clientId;

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }
    }
}
