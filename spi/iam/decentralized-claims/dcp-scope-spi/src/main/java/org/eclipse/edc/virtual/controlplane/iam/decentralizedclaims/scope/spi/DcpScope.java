// java
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

package org.eclipse.edc.virtual.controlplane.iam.decentralizedclaims.scope.spi;

import java.util.Objects;

/**
 * Represents a scope used by  control plane.
 *
 * <p>A {@code DcpScope} encapsulates an identifier, a typed scope classification
 * (see {@link DcpScope.DcpScopeType}), a value that identifies the actual scope,
 * an optional prefix mapping (required for {@link DcpScope.DcpScopeType#POLICY}),
 * and a profile string. By default, the {@code profile} is set to {@link #WILDCARD}
 * and the {@code type} defaults to {@link DcpScopeType#DEFAULT}.</p>
 */
public class DcpScope {

    public static final String WILDCARD = "*";

    public String profile = WILDCARD;

    private String id;

    private String type = DcpScopeType.DEFAULT.name();

    private String value;

    private String prefixMapping;

    private DcpScope() {
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public DcpScopeType typeAsEnum() {
        return DcpScopeType.valueOf(type);
    }

    public String getValue() {
        return value;
    }

    public String getProfile() {
        return profile;
    }

    public String getPrefixMapping() {
        return prefixMapping;
    }

    public enum DcpScopeType {
        DEFAULT,
        POLICY,
    }

    public static class Builder {
        private final DcpScope scope;

        private Builder(DcpScope scope) {
            this.scope = scope;
        }

        public static Builder newInstance() {
            return new Builder(new DcpScope());
        }

        public Builder id(String id) {
            this.scope.id = id;
            return this;
        }

        public Builder type(DcpScopeType type) {
            this.scope.type = type.name();
            return this;
        }

        public Builder value(String value) {
            this.scope.value = value;
            return this;
        }

        public Builder prefixMapping(String prefixMapping) {
            this.scope.prefixMapping = prefixMapping;
            return this;
        }

        public Builder profile(String profile) {
            this.scope.profile = profile;
            return this;
        }

        public DcpScope build() {
            Objects.requireNonNull(scope.id, "DcpScope id cannot be null");
            Objects.requireNonNull(scope.value, "DcpScope value cannot be null");
            Objects.requireNonNull(scope.profile, "DcpScope profile cannot be null");

            if (scope.type.equals(DcpScopeType.POLICY.name())) {
                Objects.requireNonNull(scope.prefixMapping, "DcpScope prefixMapping cannot be null for POLICY type");
            }

            if (scope.type.equals(DcpScopeType.DEFAULT.name())) {
                if (scope.prefixMapping != null) {
                    throw new IllegalArgumentException("Prefix mapping should be null for DEFAULT type");
                }
            }

            return scope;
        }
    }
}
