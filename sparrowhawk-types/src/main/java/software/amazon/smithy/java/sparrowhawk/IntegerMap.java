/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeVarintListLength;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.ulongSize;

public final class IntegerMap extends NumberMap<Integer> {
    @Override
    protected int decodeValueCount(int encodedCount) {
        return KConstants.decodeVarintListLengthChecked(encodedCount);
    }

    @Override
    protected Integer decode(SparrowhawkDeserializer d) {
        return d.varI();
    }

    @Override
    protected int sizeofValues(Integer[] elements) {
        int n = elements.length;
        int size = ulongSize(encodeVarintListLength(n));
        for (int i = 0; i < n; i++) {
            size += SparrowhawkSerializer.intSize(elements[i]);
        }
        return size;
    }

    @Override
    protected Integer[] newArray(int len) {
        return new Integer[len];
    }

    @Override
    protected void writeValues(SparrowhawkSerializer s, Integer[] values) {
        s.writeIntegerList(values);
    }
}
