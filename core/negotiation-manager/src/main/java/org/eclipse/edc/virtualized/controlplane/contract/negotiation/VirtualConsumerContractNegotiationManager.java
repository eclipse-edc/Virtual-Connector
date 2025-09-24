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

package org.eclipse.edc.virtualized.controlplane.contract.negotiation;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.ConsumerContractNegotiationManager;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.observe.ContractNegotiationObservable;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequest;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.telemetry.Telemetry;

import java.util.UUID;

import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation.Type.CONSUMER;

/**
 * An implementation of the {@link ConsumerContractNegotiationManager} that only handles initial contract negotiation
 * requests by transitioning them to the initial state. The subsequent state transitions are handled by the
 * by {@link org.eclipse.edc.virtualized.controlplane.contract.spi.negotiation.ContractNegotiationStateMachineService},
 * which can be invoked using different mechanisms, such as a loopback mechanism, a message bus.
 */
public class VirtualConsumerContractNegotiationManager implements ConsumerContractNegotiationManager {

    private final ContractNegotiationStore store;
    private final ContractNegotiationObservable observable;
    private final Monitor monitor;
    protected Telemetry telemetry = new Telemetry();

    public VirtualConsumerContractNegotiationManager(ContractNegotiationStore store, ContractNegotiationObservable observable, Monitor monitor) {
        this.store = store;
        this.observable = observable;
        this.monitor = monitor;
    }

    @Override
    @WithSpan
    public StatusResult<ContractNegotiation> initiate(ContractRequest request) {
        var id = UUID.randomUUID().toString();
        var negotiation = ContractNegotiation.Builder.newInstance()
                .id(id)
                .protocol(request.getProtocol())
                .counterPartyId(request.getProviderId())
                .counterPartyAddress(request.getCounterPartyAddress())
                .callbackAddresses(request.getCallbackAddresses())
                .traceContext(telemetry.getCurrentTraceContext())
                .type(CONSUMER)
                .build();

        negotiation.addContractOffer(request.getContractOffer());
        transitionToInitial(negotiation);

        return StatusResult.success(negotiation);
    }

    protected void transitionToInitial(ContractNegotiation negotiation) {
        negotiation.transitionInitial();
        update(negotiation);
        observable.invokeForEach(l -> l.initiated(negotiation));
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    protected void update(ContractNegotiation entity) {
        store.save(entity);
        monitor.debug(() -> "[%s] %s %s is now in state %s".formatted(this.getClass().getSimpleName(), entity.getClass().getSimpleName(),
                entity.getId(), entity.stateAsString()));
    }
}
