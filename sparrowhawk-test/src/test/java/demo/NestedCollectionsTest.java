/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkDeserializer;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkObject;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer;

class NestedCollectionsTest {

    private static byte[] encode(SparrowhawkObject o) {
        var s = new SparrowhawkSerializer(o.size());
        o.encodeTo(s);
        return s.payload();
    }

    private static NestedCollectionsInput roundtrip(NestedCollectionsInput input) {
        var decoded = new NestedCollectionsInput();
        decoded.decodeFrom(new SparrowhawkDeserializer(encode(input)));
        return decoded;
    }

    private static <K, V> Map<K, V> orderedMap(Object... kvs) {
        Map<K, V> m = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            m.put((K) kvs[i], (V) kvs[i + 1]);
        }
        return m;
    }

    private static ByteBuffer bb(String s) {
        return ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
    }

    private static NestedStructure struct(String s, List<Integer> ints) {
        var n = new NestedStructure();
        n.setInnerStr(s);
        n.setList(ints);
        return n;
    }

    private static TreeNode node(String name, List<List<TreeNode>> children) {
        var t = new TreeNode();
        t.setName(name);
        if (children != null) {
            t.setChildren(children);
        }
        return t;
    }

    private static NestedCollectionsInput populated() {
        var input = new NestedCollectionsInput();
        input.setIntListList(List.of(List.of(1), List.of(2, 3), List.of()));
        input.setStringListList(List.of(List.of("a", "b"), List.of(), List.of("")));
        input.setStructListList(List.of(List.of(struct("s1", List.of(1)), struct("s2", List.of())), List.of()));
        input.setBlobListList(List.of(List.of(bb("x"), bb("")), List.of()));
        input.setTimestampListList(List.of(List.of(new Date(1000), new Date(0)), List.of()));
        input.setDoubleListList(List.of(List.of(1.5, -2.5), List.of()));
        input.setIntListListList(List.of(List.of(List.of(1), List.of(2, 3)), List.of()));
        input.setIntListListListList(List.of(List.of(List.of(List.of(7, 8))), List.of()));
        input.setStringMapList(List.of(orderedMap("a", "b"), orderedMap()));
        input.setStructMapList(List.of(orderedMap("k", struct("sv", List.of(4)))));
        input.setIntMapMapList(List.of(orderedMap("k", orderedMap("l", 1))));
        input.setIntListMapList(List.of(orderedMap("k", List.of(1, 2))));
        input.setLongListMap(orderedMap("p", List.of(1L, Long.MAX_VALUE, Long.MIN_VALUE), "q", List.of()));
        input.setStringListMap(orderedMap("s", List.of("x", ""), "t", List.of()));
        input.setBlobListMap(orderedMap("b", List.of(bb("y"))));
        input.setStructListMap(orderedMap("m", List.of(struct("mv", List.of(5, 6)))));
        input.setTimestampListMap(orderedMap("t", List.of(new Date(123456789L))));
        input.setIntListListMap(orderedMap("n", List.of(List.of(1), List.of(2))));
        input.setIntListMapMap(orderedMap("o", orderedMap("p", List.of(3))));
        input.setTree(node("root", List.of(List.of(node("leaf1", null), node("leaf2", null)), List.of())));
        input.setRequiredIntListList(List.of(List.of(42)));
        return input;
    }

    @Test
    void roundtripAllFields() {
        var input = populated();
        var decoded = roundtrip(input);

        assertEquals(input.getIntListList(), decoded.getIntListList());
        assertEquals(input.getStringListList(), decoded.getStringListList());
        assertEquals(input.getStructListList(), decoded.getStructListList());
        assertEquals(input.getBlobListList(), decoded.getBlobListList());
        assertEquals(input.getTimestampListList(), decoded.getTimestampListList());
        assertEquals(input.getDoubleListList(), decoded.getDoubleListList());
        assertEquals(input.getIntListListList(), decoded.getIntListListList());
        assertEquals(input.getIntListListListList(), decoded.getIntListListListList());
        assertEquals(input.getStringMapList(), decoded.getStringMapList());
        assertEquals(input.getStructMapList(), decoded.getStructMapList());
        assertEquals(input.getIntMapMapList(), decoded.getIntMapMapList());
        assertEquals(input.getIntListMapList(), decoded.getIntListMapList());
        assertEquals(input.getLongListMap(), decoded.getLongListMap());
        assertEquals(input.getStringListMap(), decoded.getStringListMap());
        assertEquals(input.getBlobListMap(), decoded.getBlobListMap());
        assertEquals(input.getStructListMap(), decoded.getStructListMap());
        assertEquals(input.getTimestampListMap(), decoded.getTimestampListMap());
        assertEquals(input.getIntListListMap(), decoded.getIntListListMap());
        assertEquals(input.getIntListMapMap(), decoded.getIntListMapMap());
        assertEquals(input.getTree(), decoded.getTree());
        assertEquals(input.getRequiredIntListList(), decoded.getRequiredIntListList());
        assertTrue(input.equals(decoded));
    }

    @Test
    void roundtripEmptyCollections() {
        var input = new NestedCollectionsInput();
        input.setIntListList(List.of());
        input.setStringListList(List.of());
        input.setLongListMap(Map.of());
        input.setIntListListMap(Map.of());
        input.setRequiredIntListList(List.of());
        var decoded = roundtrip(input);

        assertEquals(List.of(), decoded.getIntListList());
        assertEquals(List.of(), decoded.getStringListList());
        assertEquals(Map.of(), decoded.getLongListMap());
        assertEquals(Map.of(), decoded.getIntListListMap());
        assertEquals(List.of(), decoded.getRequiredIntListList());
    }

    @Test
    void roundtripAbsentOptionals() {
        var input = new NestedCollectionsInput();
        input.setRequiredIntListList(List.of(List.of(1)));
        var decoded = roundtrip(input);

        assertEquals(null, decoded.getIntListList());
        assertEquals(null, decoded.getLongListMap());
        assertEquals(List.of(List.of(1)), decoded.getRequiredIntListList());
    }

    @Test
    void missingRequiredFieldThrows() {
        var input = new NestedCollectionsInput();
        input.setIntListList(List.of(List.of(1)));
        assertThrows(RuntimeException.class, input::size);
    }

    @Test
    void decodeThenReencodeIsByteIdentical() {
        byte[] first = encode(populated());
        var decoded = new NestedCollectionsInput();
        decoded.decodeFrom(new SparrowhawkDeserializer(first));
        byte[] second = encode(decoded);
        assertEquals(HexFormat.of().formatHex(first), HexFormat.of().formatHex(second));
    }

    @Test
    void mutateSiblingAfterDecodeAndReencode() {
        var input = populated();
        var decoded = new NestedCollectionsInput();
        decoded.decodeFrom(new SparrowhawkDeserializer(encode(input)));

        decoded.setRequiredIntListList(List.of(List.of(9), List.of()));
        var again = roundtrip(decoded);

        assertEquals(List.of(List.of(9), List.of()), again.getRequiredIntListList());
        assertEquals(input.getIntListList(), again.getIntListList());
        assertEquals(input.getStringListList(), again.getStringListList());
        assertEquals(input.getStructListMap(), again.getStructListMap());
        assertEquals(input.getIntListMapMap(), again.getIntListMapMap());
        assertEquals(input.getTree(), again.getTree());
    }

    @Test
    void gettersMemoizeConvertedValues() {
        var decoded = roundtrip(populated());
        assertSame(decoded.getIntListList(), decoded.getIntListList());
        assertSame(decoded.getStringListMap(), decoded.getStringListMap());
        assertSame(decoded.getIntListMapMap(), decoded.getIntListMapMap());
    }

    @Test
    void identicalCollectionDeclarationsShareGeneratedFlyweight() throws ReflectiveOperationException {
        var input = new NestedCollectionsInput();
        input.setIntListList(List.of(List.of(1)));
        input.setCrossNs(List.of(List.of(2)));
        input.setRequiredIntListList(List.of());
        input.size();

        assertSame(storedField(input, "intListList").getClass(), storedField(input, "crossNs").getClass());
    }

    private static Object storedField(NestedCollectionsInput input, String name) throws ReflectiveOperationException {
        var field = NestedCollectionsInput.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(input);
    }

    @Test
    void deepRecursiveTree() {
        var input = new NestedCollectionsInput();
        input.setRequiredIntListList(List.of());
        var deep = node("l0", List.of(List.of(node("l1", List.of(List.of(node("l2", null)))))));
        input.setTree(deep);
        var decoded = roundtrip(input);
        assertEquals(deep, decoded.getTree());
    }
}
