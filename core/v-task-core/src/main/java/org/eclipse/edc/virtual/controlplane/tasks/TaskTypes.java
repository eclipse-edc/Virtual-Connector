/*
 *  Copyright (c) 2026 Metaform Systems, Inc.
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

package org.eclipse.edc.virtual.controlplane.tasks;

import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.AgreeNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.FinalizeNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.RequestNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.SendAgreement;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.SendFinalizeNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.SendRequestNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.SendVerificationNegotiation;
import org.eclipse.edc.virtual.controlplane.contract.spi.negotiation.tasks.VerifyNegotiation;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.CompleteDataFlow;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.PrepareTransfer;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.ResumeDataFlow;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.SendTransferRequest;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.SignalDataflowStarted;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.StartDataflow;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.SuspendDataFlow;
import org.eclipse.edc.virtual.controlplane.transfer.spi.tasks.TerminateDataFlow;

import java.util.List;

public class TaskTypes {

    public static final List<Class<?>> TYPES = List.of(
            RequestNegotiation.class,
            SendRequestNegotiation.class,
            AgreeNegotiation.class,
            SendAgreement.class,
            VerifyNegotiation.class,
            SendVerificationNegotiation.class,
            FinalizeNegotiation.class,
            SendFinalizeNegotiation.class,
            PrepareTransfer.class,
            SendTransferRequest.class,
            StartDataflow.class,
            SignalDataflowStarted.class,
            SuspendDataFlow.class,
            ResumeDataFlow.class,
            TerminateDataFlow.class,
            CompleteDataFlow.class);
}
