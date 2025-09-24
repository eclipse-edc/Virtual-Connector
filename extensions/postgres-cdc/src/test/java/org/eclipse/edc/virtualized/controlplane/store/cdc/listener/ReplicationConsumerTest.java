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

package org.eclipse.edc.virtualized.controlplane.store.cdc.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.ContractNegotiationChangeListener;
import org.eclipse.edc.virtualized.controlplane.store.cdc.DatabaseChange;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.TransferProcessChangeListener;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.FINALIZED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.REQUESTED;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ReplicationConsumerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ContractNegotiationChangeListener negotiationChangeListener = mock();
    private final TransferProcessChangeListener transferProcessChangeListener = mock();
    private final ReplicationConsumer replicationConsumer = new ReplicationConsumer(negotiationChangeListener, transferProcessChangeListener, () -> mapper);

    @Test
    void apply() {

        var builder = ContractNegotiation.Builder.newInstance().id("id")
                .type(ContractNegotiation.Type.PROVIDER)
                .state(REQUESTED.code())
                .counterPartyId("counterparty-id")
                .counterPartyAddress("counterparty-address")
                .stateCount(0)
                .stateTimestamp(0)
                .createdAt(0)
                .updatedAt(0)
                .pending(false)
                .protocol("protocol");

        var before = builder.build().copy();
        var after = builder.state(FINALIZED.code()).build();

        var change = DatabaseChange.Builder.newInstance()
                .schema("public")
                .table("edc_contract_negotiation")
                .action(DatabaseChange.Action.UPDATE)
                .columns(toChange(after))
                .identity(toChange(before))
                .build();

        when(negotiationChangeListener.onChange(isNotNull(), isNotNull())).thenReturn(StatusResult.success());

        var result = replicationConsumer.apply(change);
        assertThat(result).isSucceeded();

        verify(negotiationChangeListener).onChange(eq(before), eq(after));
    }

    @Test
    void apply_noChanges() {

        var builder = ContractNegotiation.Builder.newInstance().id("id")
                .type(ContractNegotiation.Type.PROVIDER)
                .state(REQUESTED.code())
                .counterPartyId("counterparty-id")
                .counterPartyAddress("counterparty-address")
                .stateCount(0)
                .stateTimestamp(0)
                .createdAt(0)
                .updatedAt(0)
                .pending(false)
                .protocol("protocol");

        var before = builder.build().copy();
        var after = builder.build();

        var change = DatabaseChange.Builder.newInstance()
                .schema("public")
                .table("edc_contract_negotiation")
                .action(DatabaseChange.Action.UPDATE)
                .columns(toChange(after))
                .identity(toChange(before))
                .build();

        var result = replicationConsumer.apply(change);
        assertThat(result).isSucceeded();
        verifyNoInteractions(negotiationChangeListener);
    }

    @Test
    void apply_ignoredTable() {

        var builder = ContractNegotiation.Builder.newInstance().id("id")
                .type(ContractNegotiation.Type.PROVIDER)
                .state(REQUESTED.code())
                .counterPartyId("counterparty-id")
                .counterPartyAddress("counterparty-address")
                .stateCount(0)
                .stateTimestamp(0)
                .createdAt(0)
                .updatedAt(0)
                .pending(false)
                .protocol("protocol");

        var before = builder.build().copy();
        var after = builder.build();

        var change = DatabaseChange.Builder.newInstance()
                .schema("public")
                .table("ignored")
                .action(DatabaseChange.Action.UPDATE)
                .columns(toChange(after))
                .identity(toChange(before))
                .build();

        var result = replicationConsumer.apply(change);
        assertThat(result).isSucceeded();
        verifyNoInteractions(negotiationChangeListener);
    }


    private List<DatabaseChange.Column> toChange(ContractNegotiation cn) {
        return List.of(new DatabaseChange.Column("id", "id"),
                new DatabaseChange.Column("type", cn.getType().name()),
                new DatabaseChange.Column("state", cn.getState()),
                new DatabaseChange.Column("counterparty_id", cn.getCounterPartyId()),
                new DatabaseChange.Column("counterparty_address", cn.getCounterPartyAddress()),
                new DatabaseChange.Column("state_count", cn.getStateCount()),
                new DatabaseChange.Column("state_timestamp", cn.getStateTimestamp()),
                new DatabaseChange.Column("created_at", cn.getCreatedAt()),
                new DatabaseChange.Column("updated_at", cn.getUpdatedAt()),
                new DatabaseChange.Column("pending", cn.isPending()),
                new DatabaseChange.Column("protocol", cn.getProtocol()),
                new DatabaseChange.Column("contract_offers", "[]"),
                new DatabaseChange.Column("callback_addresses", "[]"),
                new DatabaseChange.Column("protocol_messages", "{}"),
                new DatabaseChange.Column("trace_context", "{}"));
    }
}
