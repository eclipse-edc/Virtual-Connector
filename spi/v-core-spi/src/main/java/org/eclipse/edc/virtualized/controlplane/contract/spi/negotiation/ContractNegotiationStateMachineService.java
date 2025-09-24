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

package org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation;

import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.runtime.metamodel.annotation.ExtensionPoint;
import org.eclipse.edc.spi.response.StatusResult;

/**
 * A service interface for notifying the state machine of contract negotiations.
 * Implementations can be registered to handle state changes in contract negotiations.
 */
@ExtensionPoint
public interface ContractNegotiationStateMachineService {

    /**
     * Handles a state change for a contract negotiation.
     *
     * @param negotiationId the ID of the contract negotiation
     * @param state         the state of the contract negotiation
     * @return a StatusResult indicating success or failure
     */
    StatusResult<Void> handle(String negotiationId, ContractNegotiationStates state);

}
