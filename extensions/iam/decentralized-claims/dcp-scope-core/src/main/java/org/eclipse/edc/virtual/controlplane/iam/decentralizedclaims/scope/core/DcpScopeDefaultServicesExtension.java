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

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.virtual.controlplane.iam.decentralizedclaims.scope.core.defaults.InMemoryDcpScopeStore;
import org.eclipse.edc.virtual.controlplane.iam.decentralizedclaims.scope.spi.DcpScopeStore;

import static org.eclipse.edc.virtual.controlplane.iam.decentralizedclaims.scope.core.DcpScopeDefaultServicesExtension.NAME;

@Extension(NAME)
public class DcpScopeDefaultServicesExtension implements ServiceExtension {

    public static final String NAME = "DCP Scope Default Services Extension";

    @Inject
    private CriterionOperatorRegistry criterionOperatorRegistry;

    @Provider(isDefault = true)
    public DcpScopeStore scopeStore() {
        return new InMemoryDcpScopeStore(criterionOperatorRegistry);
    }
}
