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

package org.eclipse.edc.virtualized;

import org.eclipse.edc.connector.controlplane.services.spi.asset.AssetService;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogService;
import org.eclipse.edc.connector.controlplane.services.spi.contractdefinition.ContractDefinitionService;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.connector.controlplane.services.spi.policydefinition.PolicyDefinitionService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.junit.extensions.ComponentRuntimeContext;
import org.eclipse.edc.junit.utils.Endpoints;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.virtualized.transfer.fixtures.VirtualConnector;

import java.net.URI;
import java.util.HashMap;

import static org.eclipse.edc.util.io.Ports.getFreePort;

public interface Runtimes {
    
    interface ControlPlane {
        String NAME = "controlplane";

        String[] MODULES = {
                ":system-tests:runtimes:e2e:e2e-controlplane-memory",
        };

        String[] PG_MODULES = {
                ":system-tests:runtimes:e2e:e2e-controlplane-postgres",
        };

        Endpoints.Builder ENDPOINTS = Endpoints.Builder.newInstance()
                .endpoint("control", () -> URI.create("http://localhost:" + getFreePort() + "/control"))
                .endpoint("protocol", () -> URI.create("http://localhost:" + getFreePort() + "/protocol"));

        static Config config() {
            return ConfigFactory.fromMap(new HashMap<>() {
                {
                    put("edc.transfer.proxy.token.signer.privatekey.alias", "private-key");
                    put("edc.transfer.proxy.token.verifier.publickey.alias", "public-key");
                }
            });
        }

        static VirtualConnector connector(ComponentRuntimeContext ctx) {
            return new VirtualConnector(
                    ctx.getService(ParticipantContextService.class),
                    ctx.getService(ParticipantContextConfigService.class),
                    ctx.getService(AssetService.class),
                    ctx.getService(PolicyDefinitionService.class),
                    ctx.getService(ContractDefinitionService.class),
                    ctx.getService(CatalogService.class),
                    ctx.getService(ContractNegotiationService.class),
                    ctx.getService(TransferProcessService.class),
                    ctx.getEndpoint("protocol")
            );
        }
    }
}
