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

package org.eclipse.edc.virtual;

import org.eclipse.edc.junit.utils.Endpoints;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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

        String[] DCP_PG_MODULES = {
                ":system-tests:runtimes:e2e:e2e-dcp-controlplane-postgres",
        };

        Endpoints.Builder ENDPOINTS = Endpoints.Builder.newInstance()
                .endpoint("control", () -> URI.create("http://localhost:" + getFreePort() + "/control"))
                .endpoint("protocol", () -> URI.create("http://localhost:" + getFreePort() + "/protocol"))
                .endpoint("management", () -> URI.create("http://localhost:" + getFreePort() + "/management"));

        static Config config() {
            return ConfigFactory.fromMap(new HashMap<>() {
                {
                    put("edc.transfer.proxy.token.signer.privatekey.alias", "private-key");
                    put("edc.transfer.proxy.token.verifier.publickey.alias", "public-key");
                    put("edc.iam.oauth2.jwks.url", "https://example.com/jwks");
                    put("edc.iam.oauth2.issuer", "test-issuer");
                    put("edc.encryption.strict", "false");
                }
            });
        }
    }

    interface Issuer {
        String NAME = "issuer";

        String[] MODULES = {
                ":system-tests:runtimes:issuer",
        };
    }

    interface IdentityHub {
        String NAME = "identityhub";

        String[] MODULES = {
                ":system-tests:runtimes:identity-hub",
        };

        static String didFor(Endpoints endpoints, String participantContextId) {
            var didEndpoint = Objects.requireNonNull(endpoints.getEndpoint("did"));
            String didLocation = String.format("%s%%3A%s", didEndpoint.get().getHost(), didEndpoint.get().getPort());
            return String.format("did:web:%s:%s", didLocation, participantContextId);
        }

        static Config dcpConfig(Endpoints endpoints, String participantContextId) {
            var did = didFor(endpoints, participantContextId);
            var stsEndpoint = Objects.requireNonNull(endpoints.getEndpoint("sts"));
            return ConfigFactory.fromMap(Map.of(
                    "edc.participant.id", did,
                    "edc.iam.issuer.id", did,
                    "edc.iam.sts.oauth.client.id", did,
                    "edc.iam.sts.oauth.client.secret.alias", did + "-alias",
                    "edc.iam.sts.oauth.token.url", stsEndpoint.get().toString() + "/token",
                    "edc.iam.did.web.use.https", "false"
            ));
        }
    }
}
