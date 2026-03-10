/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.sparrowhawk.codegen;

import java.util.Objects;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyBuilder;

public final class SparrowhawkSettings {
    private final ShapeId service;
    private final String classPrefix;
    private final boolean zeroCopyBuffers;

    private SparrowhawkSettings(SparrowhawkSettingsBuilder builder) {
        this.service = Objects.requireNonNull(builder.service, "service not set");
        this.classPrefix = Objects.requireNonNull(builder.classPrefix, "classPrefix not set");
        this.zeroCopyBuffers = builder.zeroCopyBuffers;
    }

    public ShapeId getService() {
        return service;
    }

    public String getClassPrefix() {
        return classPrefix;
    }

    public boolean zeroCopyBuffers() {
        return zeroCopyBuffers;
    }

    public static SparrowhawkSettingsBuilder builder() {
        return new SparrowhawkSettingsBuilder();
    }

    public static final class SparrowhawkSettingsBuilder implements SmithyBuilder<SparrowhawkSettings> {
        private ShapeId service;
        private String classPrefix = "";
        private boolean zeroCopyBuffers = true;

        private SparrowhawkSettingsBuilder() {

        }

        public SparrowhawkSettingsBuilder service(ShapeId service) {
            this.service = service;
            return this;
        }

        public SparrowhawkSettingsBuilder classPrefix(String classPrefix) {
            this.classPrefix = classPrefix;
            return this;
        }

        public SparrowhawkSettingsBuilder zeroCopyBuffers(boolean copyBuffers) {
            this.zeroCopyBuffers = copyBuffers;
            return this;
        }

        @Override
        public SparrowhawkSettings build() {
            return new SparrowhawkSettings(this);
        }
    }
}
