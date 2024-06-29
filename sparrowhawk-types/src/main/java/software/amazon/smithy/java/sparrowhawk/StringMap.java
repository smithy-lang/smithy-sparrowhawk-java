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
import java.util.Iterator;
import java.util.Map;

public final class StringMap implements SparrowhawkObject {
    private static final long REQUIRED_LIST_FIELDSET_0 = KConstants.listField(0b11);
    private static final ByteBuffer[] EMPTY = new ByteBuffer[0];

    private transient int $size = -1;
    private ByteBuffer[] keys;
    private ByteBuffer[] values;

    public void fromMap(Map<String, String> m) {
        int len = m.size();
        if (len == 0) {
            keys = values = EMPTY;
            $size = 0;
            return;
        }

        ByteBuffer[] keys = new ByteBuffer[len];
        ByteBuffer[] values = new ByteBuffer[len];
        this.keys = keys;
        this.values = values;
        // fieldset + 2List lengths
        int size = 1 + (2 * uintSize(encodeLenPrefixedListLength(len)));
        size += encodeEntries(m, keys, values);
        $size = size;
    }

    private static int encodeEntries(Map<String, String> m, ByteBuffer[] keys, ByteBuffer[] values) {
        if (keys.length != values.length) throw new IllegalArgumentException();
        if (keys.length == 0) return 0;
        Iterator<Map.Entry<String, String>> iter = m.entrySet().iterator();
        int s = 0;
        for (int i = 0; i < keys.length; i++) {
            Map.Entry<String, String> e = iter.next();
            s += encodeEntry(e, keys, values, i);
        }
        return s;
    }

    private static int encodeEntry(Map.Entry<String, String> e, ByteBuffer[] keys, ByteBuffer[] values, int i) {
        byte[] key = k(e);
        byte[] value = v(e);
        keys[i] = ByteBuffer.wrap(key);
        values[i] = ByteBuffer.wrap(value);
        return l(key, value);
    }

    private static byte[] k(Map.Entry<String, String> e) {
        return e.getKey().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] v(Map.Entry<String, String> e) {
        return e.getValue().getBytes(StandardCharsets.UTF_8);
    }

    private static int l(byte[] key, byte[] value) {
        return byteListLengthEncodedSize(key.length) + byteListLengthEncodedSize(value.length);
    }

    public Map<String, String> toMap() {
        int sz = keys.length;
        Map<String, String> m = new HashMap<>(sz / 3 * 4);
        for (int i = 0; i < sz; i++) {
            m.put(string(keys[i]), string(values[i]));
        }
        return m;
    }

    private static String string(ByteBuffer b) {
        return new String(b.array(), b.arrayOffset() + b.position(), b.remaining(), StandardCharsets.UTF_8);
    }

    @Override
    public void decodeFrom(SparrowhawkDeserializer d) {
        $size = (int) decodeElementCount(d.varUI());
        if ($size > 0) {
            long fieldset = d.varUL();
            if ((fieldset & 3) != KConstants.T_LIST) {
                throw new RuntimeException("bad field type: " + KConstants.fieldType(fieldset));
            }
            if ((fieldset & REQUIRED_LIST_FIELDSET_0) != REQUIRED_LIST_FIELDSET_0) {
                throw new RuntimeException("missing required fields");
            }
            int nkeys = (int) decodeElementCount(d.varUI());
            keys = readBytes(d, nkeys);
            int nvalues = (int) decodeElementCount(d.varUI());
            if (nkeys != nvalues) {
                throw new RuntimeException("mismatch in key and value lengths");
            }
            values = readBytes(d, nvalues);
        } else {
            keys = values = EMPTY;
        }
    }

    private ByteBuffer[] readBytes(SparrowhawkDeserializer d, int n) {
        ByteBuffer[] bs = new ByteBuffer[n];
        for (int i = 0; i < bs.length; i++) {
            bs[i] = d.bytes();
        }
        return bs;
    }

    @Override
    public void encodeTo(SparrowhawkSerializer s) {
        int size = size();
        s.writeVarUL(encodeByteListLength(size));
        if (size > 0) {
            s.writeVarUL(REQUIRED_LIST_FIELDSET_0);
            long dl = encodeLenPrefixedListLength(keys.length);
            s.writeVarUL(dl);
            for (int i = 0; i < keys.length; i++) {
                s.writeBytes(keys[i]);
            }
            s.writeVarUL(dl);
            for (int i = 0; i < values.length; i++) {
                s.writeBytes(values[i]);
            }
        }
    }

    @Override
    public int size() {
        int size = this.$size;
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
            for (int i = 0; i < values.length; i++) {
                size += byteListLengthEncodedSize(values[i].remaining());
            }
        } else {
            size = 0; // 0 size written as 1 byte
        }

        this.$size = size;
        return size;
    }

    private static int invalidMap() {
        throw new RuntimeException("invalid map");
    }
}
