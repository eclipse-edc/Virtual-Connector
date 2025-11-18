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

package org.eclipse.virtualized.api.management.schema;

public interface VirtualManagementApiJsonSchema {

    String VIRTUAL_EDC_MGMT_V4_SCHEMA_PREFIX = "https://w3id.org/edc/virtual-connector/management/schema/v4";

    interface V4 {

        String CEL_EXPRESSION = VIRTUAL_EDC_MGMT_V4_SCHEMA_PREFIX + "/cel-expression-schema.json";
    }
}
