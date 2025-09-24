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

import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyArchive;
import org.eclipse.edc.connector.controlplane.transfer.spi.TransferProcessManager;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessObservable;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferRequest;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.telemetry.Telemetry;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.TransferProcessStateMachineService;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess.Type.CONSUMER;

/**
 * An implementation of the {@link TransferProcessManager} that only handles initial transfer
 * requests by transitioning them to the initial state. The subsequent state transitions are handled by the
 * by {@link TransferProcessStateMachineService},
 * which can be invoked using different mechanisms, such as a loopback mechanism, a message bus.
 */
public class VirtualTransferProcessManager implements TransferProcessManager {

    private final TransferProcessStore store;
    private final TransferProcessObservable observable;
    private final PolicyArchive policyArchive;
    private final Clock clock;
    private final Monitor monitor;
    protected Telemetry telemetry = new Telemetry();

    public VirtualTransferProcessManager(TransferProcessStore store, TransferProcessObservable observable, PolicyArchive policyArchive, Clock clock, Monitor monitor) {
        this.store = store;
        this.observable = observable;
        this.policyArchive = policyArchive;
        this.clock = clock;
        this.monitor = monitor;
    }


    @Override
    public StatusResult<TransferProcess> initiateConsumerRequest(TransferRequest transferRequest) {
        var id = Optional.ofNullable(transferRequest.getId()).orElseGet(() -> UUID.randomUUID().toString());
        var existingTransferProcess = store.findForCorrelationId(id);
        if (existingTransferProcess != null) {
            return StatusResult.success(existingTransferProcess);
        }

        var policy = policyArchive.findPolicyForContract(transferRequest.getContractId());
        if (policy == null) {
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, "No policy found for contract " + transferRequest.getContractId());
        }

        var process = TransferProcess.Builder.newInstance()
                .id(id)
                .assetId(policy.getTarget())
                .dataDestination(transferRequest.getDataDestination())
                .counterPartyAddress(transferRequest.getCounterPartyAddress())
                .contractId(transferRequest.getContractId())
                .protocol(transferRequest.getProtocol())
                .type(CONSUMER)
                .clock(clock)
                .transferType(transferRequest.getTransferType())
                .privateProperties(transferRequest.getPrivateProperties())
                .callbackAddresses(transferRequest.getCallbackAddresses())
                .traceContext(telemetry.getCurrentTraceContext())
                .build();

        observable.invokeForEach(l -> l.preCreated(process));
        update(process);
        observable.invokeForEach(l -> l.initiated(process));

        return StatusResult.success(process);
    }

    @Override
    public void start() {

    }

    protected void update(TransferProcess entity) {
        store.save(entity);
        monitor.debug(() -> "[%s] %s %s is now in state %s"
                .formatted(this.getClass().getSimpleName(), entity.getClass().getSimpleName(),
                        entity.getId(), entity.stateAsString()));
    }

    @Override
    public void stop() {

    }
}
