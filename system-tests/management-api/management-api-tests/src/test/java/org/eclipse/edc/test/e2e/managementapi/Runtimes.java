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

package org.eclipse.edc.test.e2e.managementapi;

import org.eclipse.edc.junit.utils.Endpoints;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.util.io.Ports;

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
                .endpoint("management", () -> URI.create("http://localhost:" + Ports.getFreePort() + "/management"))
                .endpoint("control", () -> URI.create("http://localhost:" + getFreePort() + "/control"))
                .endpoint("protocol", () -> URI.create("http://localhost:" + getFreePort() + "/protocol"));

        static Config config() {
            return ConfigFactory.fromMap(new HashMap<>() {
                {
                    put("edc.transfer.proxy.token.signer.privatekey.alias", "private-key");
                    put("edc.transfer.proxy.token.verifier.publickey.alias", "public-key");
                    put("edc.iam.oauth2.issuer", "test-issuer");
                    put("edc.iam.oauth2.jwks.cache.validity", "0");
                    // the config value for the JWKS url is provided dynamically
                }
            });
        }
    }
}
