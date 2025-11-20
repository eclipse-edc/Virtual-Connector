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

package org.eclipse.edc.virtual.connector.api.management.schema;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;

import java.util.HashMap;
import java.util.Map;

import static org.eclipse.edc.api.management.schema.ManagementApiJsonSchema.EDC_MGMT_V4_SCHEMA_PREFIX;
import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;
import static org.eclipse.edc.virtual.connector.api.management.schema.ManagementApiSchemaValidatorExtension.NAME;
import static org.eclipse.edc.virtualized.policy.cel.model.CelExpression.CEL_EXPRESSION_TYPE_TERM;
import static org.eclipse.virtualized.api.management.schema.VirtualManagementApiJsonSchema.V4.CEL_EXPRESSION;
import static org.eclipse.virtualized.api.management.schema.VirtualManagementApiJsonSchema.VIRTUAL_EDC_MGMT_V4_SCHEMA_PREFIX;

@Extension(NAME)
public class ManagementApiSchemaValidatorExtension implements ServiceExtension {

    public static final String NAME = "Management API Schema Validator";
    public static final String V_4 = "v4";
    public static final String V_4_PREFIX = V_4 + ":";
    private static final String EDC_CLASSPATH_SCHEMA = "classpath:schema/connector/management/v4";
    private static final String VIRTUAL_EDC_CLASSPATH_SCHEMA = "classpath:schema/virtual-connector/management/v4";

    private final Map<String, String> schemaV4 = new HashMap<>() {
        {

            put(CEL_EXPRESSION_TYPE_TERM, CEL_EXPRESSION);

        }
    };

    @Inject
    private TypeManager typeManager;
    @Inject
    private JsonObjectValidatorRegistry validator;

    @Override
    public void initialize(ServiceExtensionContext context) {

        var schemaValidatorProvider = ManagementApiSchemaValidatorProvider.Builder.newInstance()
                .objectMapper(() -> typeManager.getMapper(JSON_LD))
                .prefixMapping(EDC_MGMT_V4_SCHEMA_PREFIX, EDC_CLASSPATH_SCHEMA)
                .prefixMapping(VIRTUAL_EDC_MGMT_V4_SCHEMA_PREFIX, VIRTUAL_EDC_CLASSPATH_SCHEMA)
                .build();

        registerValidatorsV4(schemaValidatorProvider);
    }

    void registerValidatorsV4(ManagementApiSchemaValidatorProvider validatorProvider) {
        schemaV4.forEach((type, schema) -> validator.register(V_4_PREFIX + type, validatorProvider.validatorFor(schema)));
    }

}
