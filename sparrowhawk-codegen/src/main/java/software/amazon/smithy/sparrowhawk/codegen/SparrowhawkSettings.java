/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.sparrowhawk.codegen;

import java.util.Objects;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;

public final class SparrowhawkSettings {
    private static final String SERVICE = "service";
    private static final String USE_INSTANT_FOR_TIMESTAMP = "useInstantForTimestamp";
    private static final String HEADER_STRING = "headerString";

    private final ShapeId service;
    private final boolean useInstant;
    private final String header;

    private SparrowhawkSettings(ShapeId service, boolean useInstant, String header) {
        this.service = service;
        this.useInstant = useInstant;
        this.header = header;
    }

    public static SparrowhawkSettings from(ObjectNode config) {
//        config.warnIfAdditionalProperties(List.of(SERVICE));
        return new SparrowhawkSettings(
            config.expectStringMember(SERVICE).expectShapeId(),
            config.expectBooleanMember(USE_INSTANT_FOR_TIMESTAMP).getValue(),
            config.getStringMemberOrDefault(HEADER_STRING, null)
        );
    }

    public ShapeId getService() {
        return Objects.requireNonNull(service, SERVICE + " not set");
    }

    public boolean useInstant() {
        return useInstant;
    }

    public String getHeader() {
        return header;
    }
}
