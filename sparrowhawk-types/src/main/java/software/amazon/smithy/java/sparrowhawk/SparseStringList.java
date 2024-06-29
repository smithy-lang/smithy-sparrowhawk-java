/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeLenPrefixedListLengthChecked;
import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeLenPrefixedListLength;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.byteListLengthEncodedSize;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class SparseStringList implements SparrowhawkObject {
    private static final OptionalBlob EMPTY_BLOB = new OptionalBlob();
    private static final OptionalBlob[] EMPTY = new OptionalBlob[0];
    private static final SparseStringList EMPTY_LIST;

    static {
        EMPTY_LIST = new SparseStringList();
        EMPTY_LIST.size = 0;
        EMPTY_LIST.values = EMPTY;
    }

    private int size;
    private OptionalBlob[] values;

    public static SparseStringList fromList(List<String> strings) {
        int len = strings.size();
        if (len == 0) {
            return EMPTY_LIST;
        }

        SparseStringList list = new SparseStringList();
        OptionalBlob[] values = new OptionalBlob[len];
        list.values = values;
        list.size = encodeValues(strings, values);
        return list;
    }

    private static int encodeValues(List<String> strings, OptionalBlob[] values) {
        if (values.length == 0) return 0;
        int size = 0;
        for (int i = 0; i < strings.size(); i++) {
            String s = strings.get(i);
            if (s != null) {
                OptionalBlob blob = new OptionalBlob();
                byte[] bytes = strings.get(i).getBytes(StandardCharsets.UTF_8);
                blob.setItem(ByteBuffer.wrap(bytes));
                values[i] = blob;
                size += byteListLengthEncodedSize(blob.size());
            } else {
                values[i] = EMPTY_BLOB;
                size += 1;
            }
        }
        return size;
    }

    public List<String> toList() {
        List<String> l = new ArrayList<>(values.length);
        for (OptionalBlob value : values) {
            if (value.hasItem()) {
                l.add(string(value.getItem()));
            } else {
                l.add(null);
            }
        }
        return l;
    }

    private static String string(ByteBuffer b) {
        return new String(b.array(), b.arrayOffset() + b.position(), b.remaining(), StandardCharsets.UTF_8);
    }

    public int elementCount() {
        return values.length;
    }

    @Override
    public void decodeFrom(SparrowhawkDeserializer d) {
        int count = decodeLenPrefixedListLengthChecked(d.varUL());
        if (count <= 0) {
            values = EMPTY;
            size = 0;
            return;
        }

        values = new OptionalBlob[count];
        for (int i = 0; i < count; i++) {
            OptionalBlob blob = new OptionalBlob();
            blob.decodeFrom(d);
            values[i] = blob;
        }
    }

    @Override
    public void encodeTo(SparrowhawkSerializer s) {
        int count = values.length;
        s.writeVarUL(encodeLenPrefixedListLength(count));
        for (int i = 0; i < count; i++) {
            values[i].encodeTo(s);
        }
    }

    @Override
    public int size() {
        int size = this.size;
        if (size >= 0) {
            return size;
        }
        for (OptionalBlob blob : values) {
            size += byteListLengthEncodedSize(blob.size());
        }
        this.size = size;
        return size;
    }
}
