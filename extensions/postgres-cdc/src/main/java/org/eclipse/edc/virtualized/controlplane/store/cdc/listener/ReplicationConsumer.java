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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.spi.entity.ProtocolMessages;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.ContractNegotiationChangeListener;
import org.eclipse.edc.virtualized.controlplane.store.cdc.DatabaseChange;
import org.eclipse.edc.virtualized.controlplane.store.cdc.Row;

import java.util.function.Function;
import java.util.function.Supplier;

public class ReplicationConsumer implements Function<DatabaseChange, StatusResult<Void>> {

    private final ContractNegotiationChangeListener listener;
    private final Supplier<ObjectMapper> objectMapperSupplier;

    public ReplicationConsumer(ContractNegotiationChangeListener listener, Supplier<ObjectMapper> objectMapperSupplier) {
        this.listener = listener;
        this.objectMapperSupplier = objectMapperSupplier;
    }

    @Override
    public StatusResult<Void> apply(DatabaseChange diff) {
        switch (diff.table()) {
            case "edc_contract_negotiation" -> {
                var before = diff.oldRow().isEmpty() ? null : toContractNegotiation(diff.oldRow());
                var after = toContractNegotiation(diff.row());
                if (before == null || before.getState() != after.getState()) {
                    return listener.onChange(before, after);
                } else {
                    return StatusResult.success();
                }
            }
            default -> {
                // Ignore other tables
                return StatusResult.success();
            }
        }
    }


    private ContractNegotiation toContractNegotiation(Row row) {
        return ContractNegotiation.Builder.newInstance()
                .id(row.getString("id"))
                .counterPartyId(row.getString("counterparty_id"))
                .counterPartyAddress(row.getString("counterparty_address"))
                .protocol(row.getString("protocol"))
                .correlationId(row.getString("correlation_id"))
                .contractAgreement(null)
                .state(row.getInt("state"))
                .stateCount(row.getInt("state_count"))
                .stateTimestamp(row.getLong("state_timestamp"))
                .contractOffers(fromJson(row.getString("contract_offers"), new TypeReference<>() {
                }))
                .callbackAddresses(fromJson(row.getString("callback_addresses"), new TypeReference<>() {
                }))
                .errorDetail(row.getString("error_detail"))
                .traceContext(fromJson(row.getString("trace_context"), new TypeReference<>() {
                }))
                .type(ContractNegotiation.Type.valueOf(row.getString("type")))
                .createdAt(row.getLong("created_at"))
                .updatedAt(row.getLong("updated_at"))
                .pending(row.getBoolean("pending"))
                .protocolMessages(fromJson(row.getString("protocol_messages"), ProtocolMessages.class))
                .build();
    }

    protected <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapperSupplier.get().readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            throw new EdcPersistenceException(e);
        }
    }

    protected <T> T fromJson(String json, Class<T> type) {

        if (json == null) {
            return null;
        }

        try {
            return objectMapperSupplier.get().readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new EdcPersistenceException(e);
        }
    }
}
