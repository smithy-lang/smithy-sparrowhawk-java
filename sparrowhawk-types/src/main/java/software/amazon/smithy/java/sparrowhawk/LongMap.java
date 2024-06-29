/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeVarintListLength;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.ulongSize;

public final class LongMap extends NumberMap<Long> {
    @Override
    protected Long[] newArray(int len) {
        return new Long[len];
    }

    @Override
    protected int decodeValueCount(int encodedCount) {
        return KConstants.decodeVarintListLengthChecked(encodedCount);
    }

    @Override
    protected Long decode(SparrowhawkDeserializer d) {
        return d.varL();
    }

    @Override
    protected void writeValues(SparrowhawkSerializer s, Long[] values) {
        s.writeLongList(values);
    }

    @Override
    protected int sizeofValues(Long[] elements) {
        int n = elements.length;
        int size = ulongSize(encodeVarintListLength(n));
        for (int i = 0; i < n; i++) {
            size += SparrowhawkSerializer.longSize(elements[i]);
        }
        return size;
    }
}
