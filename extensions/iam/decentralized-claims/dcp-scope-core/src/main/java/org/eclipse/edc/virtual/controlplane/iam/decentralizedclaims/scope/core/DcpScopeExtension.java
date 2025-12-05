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

import org.eclipse.edc.iam.decentralizedclaims.spi.scope.ScopeExtractorRegistry;
import org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestContractNegotiationPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestTransferProcessPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.edc.virtual.controlplane.iam.decentralizedclaims.scope.spi.DcpScopeRegistry;
import org.eclipse.edc.virtual.controlplane.iam.decentralizedclaims.scope.spi.DcpScopeStore;

import static org.eclipse.edc.virtual.controlplane.iam.decentralizedclaims.scope.core.DcpScopeDefaultServicesExtension.NAME;

@Extension(NAME)
public class DcpScopeExtension implements ServiceExtension {

    public static final String NAME = "DCP Scope Extension";

    @Inject
    private PolicyEngine policyEngine;

    @Inject
    private TransactionContext transactionContext;

    @Inject
    private DcpScopeStore scopeStore;


    @Inject
    private ScopeExtractorRegistry scopeExtractorRegistry;

    private DcpScopeRegistry registry;

    @Override
    public void initialize(ServiceExtensionContext context) {
        var contextMappingFunction = new DefaultScopeMappingFunction(registry());

        policyEngine.registerPostValidator(RequestCatalogPolicyContext.class, contextMappingFunction::apply);
        policyEngine.registerPostValidator(RequestContractNegotiationPolicyContext.class, contextMappingFunction::apply);
        policyEngine.registerPostValidator(RequestTransferProcessPolicyContext.class, contextMappingFunction::apply);

        scopeExtractorRegistry.registerScopeExtractor(new DynamicScopeExtractor(registry()));

    }

    @Provider
    public DcpScopeRegistry registry() {
        if (registry == null) {
            registry = new DcpScopeRegistryImpl(transactionContext, scopeStore);
        }
        return registry;
    }
}
