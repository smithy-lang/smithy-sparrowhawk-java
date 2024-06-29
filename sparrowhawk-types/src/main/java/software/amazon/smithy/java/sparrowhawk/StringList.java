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

public final class StringList implements SparrowhawkObject {
    private static final ByteBuffer[] EMPTY = new ByteBuffer[0];
    private static final StringList EMPTY_LIST;
    static {
        EMPTY_LIST = new StringList();
        EMPTY_LIST.size = 0;
        EMPTY_LIST.values = EMPTY;
    }

    private int size;
    private ByteBuffer[] values;

    public static StringList fromList(List<String> strings) {
        int len = strings.size();
        if (len == 0) {
            return EMPTY_LIST;
        }

        StringList list = new StringList();
        ByteBuffer[] values = new ByteBuffer[len];
        list.values = values;
        list.size = encodeValues(strings, values);
        return list;
    }

    private static int encodeValues(List<String> strings, ByteBuffer[] values) {
        if (values.length == 0) return 0;
        int size = 0;
        for (int i = 0; i < strings.size(); i++) {
            byte[] bytes = strings.get(i).getBytes(StandardCharsets.UTF_8);
            values[i] = ByteBuffer.wrap(bytes);
            size += byteListLengthEncodedSize(bytes.length);
        }
        return size;
    }

    public List<String> toList() {
        List<String> l = new ArrayList<>(values.length);
        for (ByteBuffer value : values) {
            l.add(string(value));
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

        values = new ByteBuffer[count];
        for (int i = 0; i < count; i++) {
            values[i] = d.bytes();
        }
    }

    @Override
    public void encodeTo(SparrowhawkSerializer s) {
        int count = values.length;
        s.writeVarUL(encodeLenPrefixedListLength(count));
        for (int i = 0; i < count; i++) {
            s.writeBytes(values[i]);
        }
    }

    @Override
    public int size() {
        int size = this.size;
        if (size >= 0) {
            return size;
        }
        if (values.length > 0) {
            for (ByteBuffer value : values) {
                int len = value.remaining();
                size += byteListLengthEncodedSize(len) + len;
            }
        } else {
            size = 0;
        }

        this.size = size;
        return size;
    }
}
