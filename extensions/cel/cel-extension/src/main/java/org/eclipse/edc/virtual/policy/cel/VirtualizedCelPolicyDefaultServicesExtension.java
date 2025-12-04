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

package org.eclipse.edc.virtual.policy.cel;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.virtual.policy.cel.store.CelExpressionStore;
import org.eclipse.edc.virtual.policy.cel.store.InMemoryCelExpressionStore;

import static org.eclipse.edc.virtual.policy.cel.VirtualizedCelPolicyExtension.NAME;

@Extension(NAME)
public class VirtualizedCelPolicyDefaultServicesExtension implements ServiceExtension {

    public static final String NAME = "EDC-V Common Expression Language Policy Default Services Extension";

    @Inject
    private CriterionOperatorRegistry criterionOperatorRegistry;

    @Override
    public String name() {
        return NAME;
    }

    @Provider(isDefault = true)
    public CelExpressionStore policyExpressionStore() {
        return new InMemoryCelExpressionStore(criterionOperatorRegistry);
    }

}
