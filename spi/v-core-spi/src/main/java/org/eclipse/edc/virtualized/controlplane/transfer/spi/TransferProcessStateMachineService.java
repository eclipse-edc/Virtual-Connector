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

package org.eclipse.edc.virtualized.controlplane.transfer.spi;

import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.runtime.metamodel.annotation.ExtensionPoint;
import org.eclipse.edc.spi.response.StatusResult;

/**
 * A service interface for notifying the state machine of transfer processes.
 * Implementations can be registered to handle state changes in transfer processes.
 */
@ExtensionPoint
public interface TransferProcessStateMachineService {

    /**
     * Handles a state change for a transfer process.
     *
     * @param transferId the ID of the transfer process
     * @param state      the state of the transfer process
     * @return a StatusResult indicating success or failure
     */
    StatusResult<Void> handle(String transferId, TransferProcessStates state);

}
