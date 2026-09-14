/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeLenPrefixedListLengthChecked;
import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeLenPrefixedListLength;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.byteListLengthEncodedSize;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.lenPrefixedListLengthEncodedSize;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.uintSize;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.ulongSize;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class NestedCollectionMapTest {

    static final class StringListMapSpec extends NestedCollectionMap<List<String>> {
        @Override
        public void fromMap(Map<String, List<String>> map) {
            int len = map.size();
            if (len == 0) {
                setEmpty();
                return;
            }
            ByteBuffer[] keys = new ByteBuffer[len];
            Object[] values = new Object[len];
            int size = 1 + 2 * uintSize(encodeLenPrefixedListLength(len));
            int i = 0;
            for (Map.Entry<String, List<String>> e : map.entrySet()) {
                byte[] key = e.getKey().getBytes(StandardCharsets.UTF_8);
                keys[i] = ByteBuffer.wrap(key);
                StringList v = StringList.fromList(e.getValue());
                values[i++] = v;
                size += byteListLengthEncodedSize(key.length)
                    + lenPrefixedListLengthEncodedSize(v.size(), v.elementCount());
            }
            init(keys, values, size);
        }

        @Override
        public Map<String, List<String>> toMap() {
            int sz = keys.length;
            Map<String, List<String>> m = new HashMap<>(sz / 3 * 4);
            for (int i = 0; i < sz; i++) {
                m.put(string(keys[i]), ((StringList) values[i]).toList());
            }
            return m;
        }

        @Override
        protected Object[] readValues(SparrowhawkDeserializer d, int n) {
            Object[] values = new Object[n];
            for (int i = 0; i < n; i++) {
                StringList v = new StringList();
                v.decodeFrom(d);
                values[i] = v;
            }
            return values;
        }

        @Override
        protected void writeValues(SparrowhawkSerializer s) {
            Object[] values = this.values;
            s.writeVarUL(encodeLenPrefixedListLength(values.length));
            for (int i = 0; i < values.length; i++) {
                ((StringList) values[i]).encodeTo(s);
            }
        }

        @Override
        protected int sizeofValues() {
            Object[] values = this.values;
            int size = ulongSize(encodeLenPrefixedListLength(values.length));
            for (int i = 0; i < values.length; i++) {
                StringList v = (StringList) values[i];
                size += lenPrefixedListLengthEncodedSize(v.size(), v.elementCount());
            }
            return size;
        }

        @Override
        protected int decodeValueCount(int encodedCount) {
            return decodeLenPrefixedListLengthChecked(encodedCount);
        }
    }

    private static byte[] encode(StringListMapSpec m) {
        SparrowhawkSerializer s = new SparrowhawkSerializer(new byte[byteListLengthEncodedSize(m.size())]);
        m.encodeTo(s);
        return s.payload();
    }

    private static StringListMapSpec decode(byte[] payload) {
        StringListMapSpec m = new StringListMapSpec();
        m.decodeFrom(new SparrowhawkDeserializer(payload));
        return m;
    }

    @Test
    public void roundtrip() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("a", List.of("one", "two"));
        map.put("b", List.of());
        map.put("", List.of(""));

        StringListMapSpec m = new StringListMapSpec();
        m.fromMap(map);
        StringListMapSpec decoded = decode(encode(m));

        assertEquals(map, decoded.toMap());
        assertEquals(m.size(), decoded.size());
    }

    @Test
    public void emptyMapIsOneByte() {
        StringListMapSpec m = new StringListMapSpec();
        m.fromMap(Map.of());
        SparrowhawkSerializer s = new SparrowhawkSerializer(new byte[1]);
        m.encodeTo(s);
        assertEquals("01", HexFormat.of().formatHex(s.payload()));

        StringListMapSpec decoded = decode(s.payload());
        assertEquals(Map.of(), decoded.toMap());
    }

    @Test
    public void goldenSingleEntry() {
        Map<String, List<String>> map = Map.of("k", List.of("v"));
        StringListMapSpec m = new StringListMapSpec();
        m.fromMap(map);
        assertEquals("213113056b13130576", HexFormat.of().formatHex(encode(m)));
    }

    @Test
    public void reencodeAfterDecodeIsByteIdentical() {
        Map<String, List<String>> map = Map.of("key", List.of("x", "yz"));
        StringListMapSpec m = new StringListMapSpec();
        m.fromMap(map);
        byte[] first = encode(m);
        byte[] second = encode(decode(first));
        assertEquals(HexFormat.of().formatHex(first), HexFormat.of().formatHex(second));
    }

    @Test
    public void keyValueCountMismatchThrows() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("a", List.of("x"));
        map.put("b", List.of("y"));
        StringListMapSpec m = new StringListMapSpec();
        m.fromMap(map);
        byte[] payload = encode(m);

        int valuesHeaderIdx = 7;
        payload[valuesHeaderIdx] = (byte) ((encodeLenPrefixedListLength(1) << 1) | 1);
        assertThrows(RuntimeException.class, () -> decode(payload));
    }

    @Test
    public void mismatchedKeyAndValueArraysAreRejected() {
        StringListMapSpec viaInit = new StringListMapSpec();
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> viaInit.init(new ByteBuffer[1], new Object[2], 5)
        );
        assertEquals("key/value length mismatch: 1 keys, 2 values", error.getMessage());

        StringListMapSpec viaFields = new StringListMapSpec();
        viaFields.keys = new ByteBuffer[]{ByteBuffer.wrap(new byte[]{'k'})};
        viaFields.values = new Object[0];
        assertThrows(IllegalStateException.class, viaFields::size);
    }
}
