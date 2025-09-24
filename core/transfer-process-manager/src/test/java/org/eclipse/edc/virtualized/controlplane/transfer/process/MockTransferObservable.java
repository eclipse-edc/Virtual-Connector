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

package org.eclipse.edc.virtualized.controlplane.transfer.process;

import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessListener;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessObservable;

import java.util.Collection;
import java.util.List;

record MockTransferObservable(TransferProcessListener listener) implements TransferProcessObservable {
    @Override
    public Collection<TransferProcessListener> getListeners() {
        return List.of(listener);
    }

    @Override
    public void registerListener(TransferProcessListener listener) {

    }

    @Override
    public void unregisterListener(TransferProcessListener listener) {

    }
}
