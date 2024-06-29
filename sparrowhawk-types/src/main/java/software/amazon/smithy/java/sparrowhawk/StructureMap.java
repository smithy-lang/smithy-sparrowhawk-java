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
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public final class StructureMap<T extends SparrowhawkObject> implements SparrowhawkObject {
    private static final long REQUIRED_LIST_FIELDSET_0 = KConstants.listField(0b11);
    private static final ByteBuffer[] EMPTY_KEYS = new ByteBuffer[0];
    private static final Object[] EMPTY_VALUES = new Object[0];

    private ByteBuffer[] keys;
    private Object[] values;
    private final Supplier<T> factory;

    public StructureMap(Supplier<T> factory) {
        this.factory = factory;
    }

    public Map<String, T> toMap() {
        int sz = keys.length;
        T[] values = (T[]) this.values;
        Map<String, T> m = new HashMap<>(sz / 3 * 4);
        for (int i = 0; i < sz; i++) {
            m.put(string(keys[i]), values[i]);
        }
        return m;
    }

    private static String string(ByteBuffer b) {
        return new String(b.array(), b.arrayOffset() + b.position(), b.remaining(), StandardCharsets.UTF_8);
    }

    public void fromMap(Map<String, T> map) {
        int len = map.size();
        if (len == 0) {
            keys = EMPTY_KEYS;
            values = EMPTY_VALUES;
            $size = 0;
            return;
        }

        ByteBuffer[] keys = new ByteBuffer[len];
        this.keys = keys;
        T[] values = (T[]) new SparrowhawkObject[len];
        this.values = values;
        int i = 0;
        int size = 1 + (2 * uintSize(encodeLenPrefixedListLength(len)));
        for (Map.Entry<String, T> entry : map.entrySet()) {
            byte[] key = entry.getKey().getBytes(StandardCharsets.UTF_8);
            keys[i] = ByteBuffer.wrap(key);
            T value = entry.getValue();
            values[i++] = value;
            size += byteListLengthEncodedSize(key.length) + byteListLengthEncodedSize(value.size());
        }
        this.$size = size;
    }

    @Override
    public void decodeFrom(SparrowhawkDeserializer d) {
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
            int nvalues = (int) decodeElementCount(d.varUI());
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
        T[] values = (T[]) new SparrowhawkObject[n];
        for (int i = 0; i < values.length; i++) {
            T obj = factory.get();
            obj.decodeFrom(d);
            values[i] = obj;
        }
        return values;
    }

    @Override
    public void encodeTo(SparrowhawkSerializer s) {
        int size = size();
        if (size > 0) {
            s.writeVarUL(encodeByteListLength(size));
            s.writeVarUL(REQUIRED_LIST_FIELDSET_0);
            long dl = encodeLenPrefixedListLength(keys.length);
            s.writeVarUL(dl);
            for (int i = 0; i < keys.length; i++) {
                s.writeBytes(keys[i]);
            }
            s.writeVarUL(dl);
            T[] values = (T[]) this.values;
            for (int i = 0; i < values.length; i++) {
                values[i].encodeTo(s);
            }
        } else {
            s.writeRawByte(EMPTY_LIST_SIZE_VARINT);
        }
    }

    private transient int $size;

    @Override
    public int size() {
        int size = $size;
        if (size >= 0) {
            return size;
        }

        if (keys.length != values.length) {
            return invalidMap();
        }

        if (keys.length > 0) {
            size = 1; // required list field 0
            size += (2 * ulongSize(encodeLenPrefixedListLength(keys.length)));
            for (int i = 0; i < keys.length; i++) {
                size += byteListLengthEncodedSize(keys[i].remaining());
            }
            T[] values = (T[]) this.values;
            for (int i = 0; i < values.length; i++) {
                size += byteListLengthEncodedSize(values[i].size());
            }
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
