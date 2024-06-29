/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.sparrowhawk.codegen;

import java.util.stream.Stream;
import software.amazon.smithy.model.shapes.Shape;

public final class Util {
    public static boolean isStructure(Shape shape) {
        return shape.isStructureShape() || shape.isUnionShape();
    }

    public static <T> Iterable<T> iter(Stream<T> stream) {
        return stream::iterator;
    }
}
