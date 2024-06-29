/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeVarintListLength;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.ulongSize;

public final class ShortMap extends NumberMap<Short> {
    @Override
    protected Short[] newArray(int len) {
        return new Short[len];
    }

    @Override
    protected int decodeValueCount(int encodedCount) {
        return KConstants.decodeVarintListLengthChecked(encodedCount);
    }

    @Override
    protected Short decode(SparrowhawkDeserializer d) {
        return (short) d.varI();
    }

    @Override
    protected void writeValues(SparrowhawkSerializer s, Short[] values) {
        s.writeShortList(values);
    }

    @Override
    protected int sizeofValues(Short[] elements) {
        int n = elements.length;
        int size = ulongSize(encodeVarintListLength(n));
        for (int i = 0; i < n; i++) {
            size += SparrowhawkSerializer.intSize(elements[i]);
        }
        return size;
    }
}
