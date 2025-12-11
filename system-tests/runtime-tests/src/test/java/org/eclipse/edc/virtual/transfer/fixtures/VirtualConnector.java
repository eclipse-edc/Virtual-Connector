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

package org.eclipse.edc.virtual.transfer.fixtures;

import org.eclipse.edc.junit.extensions.ComponentRuntimeContext;
import org.eclipse.edc.junit.utils.LazySupplier;

import java.net.URI;
import java.util.function.Function;

public class VirtualConnector {

    private final Function<Class<?>, ?> serviceLocator;
    private final LazySupplier<URI> protocolEndpoint;

    public VirtualConnector(
            Function<Class<?>, ?> serviceLocator,
            LazySupplier<URI> protocolEndpoint) {
        this.serviceLocator = serviceLocator;
        this.protocolEndpoint = protocolEndpoint;
    }

    public static VirtualConnector forContext(ComponentRuntimeContext ctx) {
        return new VirtualConnector(
                ctx::getService,
                ctx.getEndpoint("protocol")
        );
    }

    public LazySupplier<URI> getProtocolEndpoint() {
        return protocolEndpoint;
    }

    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> serviceClass) {
        return (T) serviceLocator.apply(serviceClass);
    }

}
