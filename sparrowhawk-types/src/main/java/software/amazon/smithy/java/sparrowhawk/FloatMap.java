/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeFourBListLength;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.ulongSize;

public final class FloatMap extends NumberMap<Float> {
    @Override
    protected Float[] newArray(int len) {
        return new Float[len];
    }

    @Override
    protected int decodeValueCount(int encodedCount) {
        return KConstants.decodeFourByteListLengthChecked(encodedCount);
    }

    @Override
    protected Float decode(SparrowhawkDeserializer d) {
        return d.f4();
    }

    @Override
    protected void writeValues(SparrowhawkSerializer s, Float[] values) {
        s.writeFloatList(values);
    }

    @Override
    protected int sizeofValues(Float[] elements) {
        int n = elements.length;
        int size = ulongSize(encodeFourBListLength(n));
        return size + (4 * n);
    }
}
