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

package org.eclipse.edc.virtualized.extensions;

import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.engine.spi.AtomicConstraintRuleFunction;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.result.Result;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class MembershipCredentialEvaluationFunction<C extends ParticipantAgentPolicyContext> implements AtomicConstraintRuleFunction<Permission, C> {
    public static final String MEMBERSHIP_CONSTRAINT_KEY = "MembershipCredential";
    protected static final String EXAMPLE_NAMESPACE = "https://w3id.org/example/credentials/";
    private static final String VC_CLAIM = "vc";
    private static final String MEMBERSHIP_CLAIM = "membership";
    private static final String SINCE_CLAIM = "since";
    private static final String ACTIVE = "active";

    private MembershipCredentialEvaluationFunction() {
    }

    public static <C extends ParticipantAgentPolicyContext> MembershipCredentialEvaluationFunction<C> create() {
        return new MembershipCredentialEvaluationFunction<>() {
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean evaluate(Operator operator, Object rightOperand, Permission permission, C policyContext) {
        if (!operator.equals(Operator.EQ)) {
            policyContext.reportProblem("Invalid operator '%s', only accepts '%s'".formatted(operator, Operator.EQ));
            return false;
        }
        if (!ACTIVE.equals(rightOperand)) {
            policyContext.reportProblem("Right-operand must be equal to '%s', but was '%s'".formatted(ACTIVE, rightOperand));
            return false;
        }

        var pa = policyContext.participantAgent();
        if (pa == null) {
            policyContext.reportProblem("No ParticipantAgent found on context.");
            return false;
        }
        var credentialResult = getCredentialList(pa);
        if (credentialResult.failed()) {
            policyContext.reportProblem(credentialResult.getFailureDetail());
            return false;
        }

        return credentialResult.getContent()
                .stream()
                .filter(vc -> vc.getType().stream().anyMatch(t -> t.endsWith(MEMBERSHIP_CONSTRAINT_KEY)))
                .flatMap(vc -> vc.getCredentialSubject().stream().filter(cs -> cs.getClaims().containsKey(MEMBERSHIP_CLAIM)))
                .anyMatch(credential -> {
                    var membershipClaim = (Map<String, ?>) credential.getClaim(EXAMPLE_NAMESPACE, MEMBERSHIP_CLAIM);
                    var membershipStartDate = Instant.parse(membershipClaim.get(SINCE_CLAIM).toString());
                    return membershipStartDate.isBefore(Instant.now());
                });
    }

    protected Result<List<VerifiableCredential>> getCredentialList(ParticipantAgent agent) {
        var vcListClaim = agent.getClaims().get(VC_CLAIM);

        if (vcListClaim == null) {
            return Result.failure("ParticipantAgent did not contain a '%s' claim.".formatted(VC_CLAIM));
        }
        if (!(vcListClaim instanceof List)) {
            return Result.failure("ParticipantAgent contains a '%s' claim, but the type is incorrect. Expected %s, received %s.".formatted(VC_CLAIM, List.class.getName(), vcListClaim.getClass().getName()));
        }
        var vcList = (List<VerifiableCredential>) vcListClaim;
        if (vcList.isEmpty()) {
            return Result.failure("ParticipantAgent contains a '%s' claim but it did not contain any VerifiableCredentials.".formatted(VC_CLAIM));
        }
        return Result.success(vcList);
    }
}
