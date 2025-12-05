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

package org.eclipse.edc.virtual.controlplane.iam.decentralizedclaims.scope.core;

import org.eclipse.edc.iam.decentralizedclaims.spi.scope.ScopeExtractor;
import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.virtual.controlplane.iam.decentralizedclaims.scope.spi.DcpScope;
import org.eclipse.edc.virtual.controlplane.iam.decentralizedclaims.scope.spi.DcpScopeRegistry;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extracts scopes dynamically from the DCP scope registry based on the request context.
 */
public class DynamicScopeExtractor implements ScopeExtractor {
    private final DcpScopeRegistry registry;

    public DynamicScopeExtractor(DcpScopeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Set<String> extractScopes(Object leftValue, Operator operator, Object rightValue, RequestPolicyContext context) {
        Set<String> scopes = Set.of();
        var result = registry.getScopeMapping();
        if (result.failed()) {
            context.reportProblem("Failed to get scope mapping: " + result.getFailureMessages());
            return scopes;
        }
        return result.getContent().stream().filter(scope -> filterScope(scope, leftValue, context))
                .map(DcpScope::getValue)
                .collect(Collectors.toSet());

    }

    private boolean filterScope(DcpScope scope, Object leftValue, RequestPolicyContext context) {
        if (leftValue instanceof String leftOperand) {
            return (leftOperand.startsWith(scope.getPrefixMapping())) &&
                    (scope.getProfile().equals(DcpScope.WILDCARD) || scope.getProfile().equals(context.requestContext().getMessage().getProtocol()));
        } else {
            return false;
        }
    }
}
