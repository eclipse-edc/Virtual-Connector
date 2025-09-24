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

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.io.IOException;

import static org.eclipse.edc.virtualized.EdcVirtualizedExtension.NAME;

@Extension(NAME)
public class EdcVirtualizedExtension implements ServiceExtension {

    public static final String NAME = " EDC-V Extension";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        try (var banner = getClass().getClassLoader().getResourceAsStream("banner.txt")) {
            if (banner != null) {
                System.out.println(new String(banner.readAllBytes()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
