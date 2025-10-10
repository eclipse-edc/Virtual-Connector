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

package org.eclipse.edc.virtualized.fixtures;

import org.eclipse.edc.connector.controlplane.test.system.utils.LazySupplier;
import org.eclipse.edc.junit.extensions.EmbeddedRuntime;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

import java.net.URI;
import java.util.HashMap;

import static org.eclipse.edc.boot.BootServicesExtension.PARTICIPANT_ID;
import static org.eclipse.edc.util.io.Ports.getFreePort;

public class ControlPlaneExtension extends ComponentExtension {

    protected final LazySupplier<URI> controlPlaneDefault = new LazySupplier<>(() -> URI.create("http://localhost:" + getFreePort()));
    protected final LazySupplier<URI> controlPlaneControl = new LazySupplier<>(() -> URI.create("http://localhost:" + getFreePort() + "/control"));
    protected final LazySupplier<URI> controlPlaneProtocol = new LazySupplier<>(() -> URI.create("http://localhost:" + getFreePort() + "/dsp"));
    protected final URI controlPlaneVersion = URI.create("http://localhost:" + getFreePort() + "/version");
    private final ControlPlaneRuntime controlPlaneRuntime;

    private ControlPlaneExtension(EmbeddedRuntime runtime) {
        super(runtime);
        this.controlPlaneRuntime = new ControlPlaneRuntime(this);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        if (isParameterSupported(parameterContext, ControlPlaneRuntime.class)) {
            return true;
        }

        return super.supportsParameter(parameterContext, extensionContext);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        if (isParameterSupported(parameterContext, ControlPlaneRuntime.class)) {
            return controlPlaneRuntime;
        }
        return super.resolveParameter(parameterContext, extensionContext);
    }

    @Override
    public Config getConfiguration() {
        return ConfigFactory.fromMap(new HashMap<>() {
            {
                put(PARTICIPANT_ID, id);
                put("web.http.port", String.valueOf(controlPlaneDefault.get().getPort()));
                put("web.http.path", "/api");
                put("web.http.protocol.port", String.valueOf(controlPlaneProtocol.get().getPort()));
                put("web.http.protocol.path", controlPlaneProtocol.get().getPath());
                put("web.http.version.port", String.valueOf(controlPlaneVersion.getPort()));
                put("web.http.version.path", controlPlaneVersion.getPath());
                put("web.http.control.port", String.valueOf(controlPlaneControl.get().getPort()));
                put("web.http.control.path", controlPlaneControl.get().getPath());
                put("edc.dsp.callback.address", controlPlaneProtocol.get().toString());
                put("edc.transfer.proxy.token.signer.privatekey.alias", "private-key");
                put("edc.transfer.proxy.token.verifier.publickey.alias", "public-key");
            }
        });
    }

    public URI getControlPlaneProtocol() {
        return controlPlaneProtocol.get();
    }

    public static class Builder extends ComponentExtension.Builder<ControlPlaneExtension, Builder> {

        protected Builder() {
        }

        public static Builder newInstance() {
            return new Builder();
        }

        protected ControlPlaneExtension internalBuild() {
            return new ControlPlaneExtension(this.runtime);
        }
    }
}
