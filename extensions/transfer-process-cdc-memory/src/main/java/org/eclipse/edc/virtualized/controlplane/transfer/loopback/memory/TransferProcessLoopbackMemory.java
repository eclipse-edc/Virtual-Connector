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

package org.eclipse.edc.virtualized.controlplane.transfer.loopback.memory;

import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.TransferProcessChangeListener;
import org.eclipse.edc.virtualized.controlplane.transfer.spi.TransferProcessStateMachineService;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class TransferProcessLoopbackMemory implements TransferProcessChangeListener {

    private final BlockingQueue<TransferProcessDiff> diffs = new ArrayBlockingQueue<>(100);
    private final AtomicBoolean active = new AtomicBoolean();
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final TransferProcessStateMachineService stateMachineService;
    private final Monitor monitor;

    public TransferProcessLoopbackMemory(TransferProcessStateMachineService stateMachineService, Monitor monitor) {
        this.stateMachineService = stateMachineService;
        this.monitor = monitor;
    }

    public void start() {
        active.set(true);
        executor.submit(() -> {
            while (active.get()) {
                try {
                    var diff = diffs.poll(10, MILLISECONDS);
                    if (diff != null) {
                        Thread.sleep(50);
                        processDiff(diff);
                    }
                } catch (InterruptedException e) {
                    monitor.severe("Event processing interrupted: " + e.getMessage());
                    break;
                }
            }
        });
    }

    public void stop() {
        active.set(false);
        executor.shutdown();
    }

    private void processDiff(TransferProcessDiff diff) {
        var state = TransferProcessStates.from(diff.after.getState());
        var result = stateMachineService.handle(diff.after.getId(), state);
        if (result.failed()) {
            monitor.severe("Failed to process transfer process state change: " + result.getFailureDetail());
        }
    }

    @Override
    public StatusResult<Void> onChange(TransferProcess before, TransferProcess after) {
        if (active.get()) {
            try {
                diffs.put(new TransferProcessDiff(before, after));
            } catch (InterruptedException e) {
                monitor.severe("Failed to put event into queue: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
            return StatusResult.success();
        } else {
            monitor.warning("TransferProcessLoopbackMemory is not active, skipping event processing.");
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, "TransferProcessLoopbackMemory is not active, skipping event processing.");
        }
    }


    private record TransferProcessDiff(TransferProcess before, TransferProcess after) {

    }

}
