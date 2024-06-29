/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import java.util.concurrent.Flow;

public interface SparrowhawkStructure<T> extends SparrowhawkObject {
    Class<? extends T> getConvertedType();

    default T convertTo() {
        throw new UnsupportedOperationException();
    }

    default T convertTo(Flow.Publisher<?> publisher) {
        throw new UnsupportedOperationException();
    }
}
