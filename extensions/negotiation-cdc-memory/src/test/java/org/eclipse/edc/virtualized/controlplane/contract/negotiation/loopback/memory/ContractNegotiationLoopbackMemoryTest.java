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

package org.eclipse.edc.virtualized.controlplane.contract.negotiation.loopback.memory;

import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.ContractNegotiationStateMachineService;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.FINALIZED;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.edc.spi.response.ResponseStatus.FATAL_ERROR;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ContractNegotiationLoopbackMemoryTest {


    private final ContractNegotiationStateMachineService stateMachineService = mock();
    private final Monitor monitor = mock();
    private final ContractNegotiationLoopbackMemory loopbackMemory = new ContractNegotiationLoopbackMemory(stateMachineService, monitor);


    @Test
    void onChange() {
        var builder = ContractNegotiation.Builder.newInstance()
                .counterPartyId("counterPartyId")
                .counterPartyAddress("counterPartyAddress")
                .protocol("protocol");

        var before = builder.build().copy();
        var after = builder.state(FINALIZED.code()).build();

        loopbackMemory.start();

        var result = loopbackMemory.onChange(before, after);

        assertThat(result).isSucceeded();

        await().untilAsserted(() -> {
            // Verify that the state machine service was called with the correct parameters
            verify(stateMachineService).handle(after.getId(), FINALIZED);
        });
    }

    @Test
    void onChange_whenStopped() {
        var builder = ContractNegotiation.Builder.newInstance()
                .counterPartyId("counterPartyId")
                .counterPartyAddress("counterPartyAddress")
                .protocol("protocol");

        var before = builder.build().copy();
        var after = builder.state(FINALIZED.code()).build();

        var result = loopbackMemory.onChange(before, after);

        assertThat(result).isFailed();

    }

    @Test
    void onChange_shouldLogProcessingFailure() {
        var builder = ContractNegotiation.Builder.newInstance()
                .counterPartyId("counterPartyId")
                .counterPartyAddress("counterPartyAddress")
                .protocol("protocol");

        var before = builder.build().copy();
        var after = builder.state(FINALIZED.code()).build();

        when(stateMachineService.handle(after.getId(), FINALIZED))
                .thenReturn(StatusResult.failure(FATAL_ERROR, "Processing failed"));
        loopbackMemory.start();

        var result = loopbackMemory.onChange(before, after);

        assertThat(result).isSucceeded();

        await().untilAsserted(() -> {
            verify(monitor).severe(contains("Failed to process contract negotiation state change:"));
        });

    }
}
