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

package org.eclipse.edc.virtualized.policy.cel.model;

import java.time.Clock;
import java.util.Set;

/**
 * Represents a CEL (Common Expression Language) expression used in policy definitions.
 *
 * @param id          the unique identifier of the CEL expression
 * @param leftOperand the left operand of the expression
 * @param expression  the CEL expression string
 * @param description a description of the expression
 * @param createdAt   the timestamp when the expression was created
 * @param updatedAt   the timestamp when the expression was last updated
 */
public record CelExpression(String id, Set<String> scopes, String leftOperand, String expression,
                            String description, Long createdAt, Long updatedAt) {


    public CelExpression(String id, String leftOperand, String expression, String description) {
        this(id, Set.of("*."), leftOperand, expression, description, Clock.systemUTC().millis(), Clock.systemUTC().millis());
    }

}
