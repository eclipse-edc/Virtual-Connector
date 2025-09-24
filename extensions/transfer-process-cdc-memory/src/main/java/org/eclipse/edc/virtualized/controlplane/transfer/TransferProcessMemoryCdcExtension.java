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

package org.eclipse.edc.virtualized.controlplane.transfer;

import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.virtualized.controlplane.transfer.store.InMemoryTransferProcessStoreCdc;
import org.eclipse.edc.virtualized.controlplane.transfer.store.TransferProcessChangeRegistry;

import java.time.Clock;

public class TransferProcessMemoryCdcExtension implements ServiceExtension {

    @Inject
    private Clock clock;

    @Inject
    private CriterionOperatorRegistry criterionOperatorRegistry;

    private InMemoryTransferProcessStoreCdc transferProcessStore;

    private InMemoryTransferProcessStoreCdc transferProcessStoreImpl() {
        if (transferProcessStore == null) {
            transferProcessStore = new InMemoryTransferProcessStoreCdc(clock, criterionOperatorRegistry);
        }
        return transferProcessStore;
    }

    @Provider
    public TransferProcessStore transferProcessStore() {
        return transferProcessStoreImpl();
    }

    @Provider
    public TransferProcessChangeRegistry contractNegotiationChangeRegistry() {
        return transferProcessStoreImpl();
    }
}
