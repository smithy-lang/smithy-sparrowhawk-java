/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.*;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
public abstract class NumberMap<T> implements SparrowhawkObject {
    private static final long REQUIRED_LIST_FIELDSET_0 = KConstants.listField(0b11);
    private static final ByteBuffer[] EMPTY_KEYS = new ByteBuffer[0];
    private static final Object[] EMPTY_VALUES = new Object[0];

    private ByteBuffer[] keys;
    private T[] values;

    public final Map<String, T> toMap() {
        int sz = keys.length;
        Map<String, T> m = new HashMap<>(sz / 3 * 4);
        for (int i = 0; i < sz; i++) {
            m.put(string(keys[i]), values[i]);
        }
        return m;
    }

    private static String string(ByteBuffer b) {
        return new String(b.array(), b.arrayOffset() + b.position(), b.remaining(), StandardCharsets.UTF_8);
    }

    public final void fromMap(Map<String, T> map) {
        int len = map.size();
        if (len == 0) {
            keys = EMPTY_KEYS;
            values = (T[]) EMPTY_VALUES;
            $size = 0;
            return;
        }

        ByteBuffer[] keys = new ByteBuffer[len];
        this.keys = keys;
        T[] values = newArray(len);
        this.values = values;
        int i = 0;
        for (Map.Entry<String, T> entry : map.entrySet()) {
            byte[] key = entry.getKey().getBytes(StandardCharsets.UTF_8);
            keys[i] = ByteBuffer.wrap(key);
            T value = entry.getValue();
            values[i++] = value;
        }
        this.$size = -1;
    }

    protected abstract T[] newArray(int len);

    @Override
    public final void decodeFrom(SparrowhawkDeserializer d) {
        int size = (int) decodeElementCount(d.varUI());
        $size = size;
        if (size > 0) {
            long fieldset = d.varUL();
            if ((fieldset & 3) != KConstants.T_LIST) {
                throw new RuntimeException("bad field type: " + KConstants.fieldType(fieldset));
            }
            if ((fieldset & REQUIRED_LIST_FIELDSET_0) != REQUIRED_LIST_FIELDSET_0) {
                throw new RuntimeException("missing required fields");
            }
            int nkeys = (int) decodeElementCount(d.varUI());
            keys = readKeys(d, nkeys);
            int nvalues = decodeValueCount(d.varUI());
            if (nkeys != nvalues) {
                throw new RuntimeException("mismatch in key and value lengths");
            }
            values = readValues(d, nvalues);
        } else {
            keys = EMPTY_KEYS;
            values = (T[]) EMPTY_VALUES;
        }
    }

    private static ByteBuffer[] readKeys(SparrowhawkDeserializer d, int n) {
        ByteBuffer[] bs = new ByteBuffer[n];
        for (int i = 0; i < bs.length; i++) {
            bs[i] = d.bytes();
        }
        return bs;
    }

    private T[] readValues(SparrowhawkDeserializer d, int n) {
        T[] values = newArray(n);
        for (int i = 0; i < values.length; i++) {
            values[i] = decode(d);
        }
        return values;
    }

    protected abstract int decodeValueCount(int encodedCount);

    protected abstract T decode(SparrowhawkDeserializer d);

    @Override
    public final void encodeTo(SparrowhawkSerializer s) {
        int size = size();
        if (size > 0) {
            s.writeVarUL(encodeByteListLength(size));
            s.writeVarUL(REQUIRED_LIST_FIELDSET_0);
            long dl = encodeLenPrefixedListLength(keys.length);
            s.writeVarUL(dl);
            for (int i = 0; i < keys.length; i++) {
                s.writeBytes(keys[i]);
            }
            writeValues(s, values);
        } else {
            s.writeRawByte(EMPTY_LIST_SIZE_VARINT);
        }
    }

    protected abstract void writeValues(SparrowhawkSerializer s, T[] values);


    private transient int $size;

    protected abstract int sizeofValues(T[] elements);

    @Override
    public final int size() {
        int size = $size;
        if (size >= 0) {
            return size;
        }

        if (keys.length != values.length) {
            return invalidMap();
        }

        if (keys.length > 0) {
            size = 1; // required list field 0
            size += ulongSize(encodeLenPrefixedListLength(keys.length));
            for (int i = 0; i < keys.length; i++) {
                size += byteListLengthEncodedSize(keys[i].remaining());
            }
            size += sizeofValues(values);
        } else {
            size = 0;
        }

        this.$size = size;
        return size;
    }

    private static int invalidMap() {
        throw new RuntimeException("invalid map");
    }
}
