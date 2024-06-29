/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeVarintListLength;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.ulongSize;

public final class ByteMap extends NumberMap<Byte> {
    @Override
    protected Byte[] newArray(int len) {
        return new Byte[len];
    }

    @Override
    protected int decodeValueCount(int encodedCount) {
        return KConstants.decodeVarintListLengthChecked(encodedCount);
    }

    @Override
    protected Byte decode(SparrowhawkDeserializer d) {
        return (byte) d.varI();
    }

    @Override
    protected void writeValues(SparrowhawkSerializer s, Byte[] values) {
        s.writeByteList(values);
    }

    @Override
    protected int sizeofValues(Byte[] elements) {
        int n = elements.length;
        int size = ulongSize(encodeVarintListLength(n));
        for (int i = 0; i < n; i++) {
            size += SparrowhawkSerializer.intSize(elements[i]);
        }
        return size;
    }
}
