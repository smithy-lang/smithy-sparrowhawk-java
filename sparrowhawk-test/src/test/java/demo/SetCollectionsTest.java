/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.sparrowhawk.ParseException;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkDeserializer;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkObject;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer;

class SetCollectionsTest {

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

    private static NestedCollectionsInput base() {
        var input = new NestedCollectionsInput();
        input.setRequiredIntListList(List.of());
        return input;
    }

    @SafeVarargs
    private static <T> Set<T> orderedSet(T... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    private static ByteBuffer bb(String s) {
        return ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void flatSetsRoundtrip() {
        var input = base();
        input.setIntSet(orderedSet(3, 1, 2));
        input.setLongSet(orderedSet(Long.MAX_VALUE, 0L, Long.MIN_VALUE));
        input.setStringSet(orderedSet("b", "", "a"));
        input.setBlobSet(orderedSet(bb("x"), bb(""), bb("y")));
        input.setBigIntegerSet(orderedSet(BigInteger.ONE.shiftLeft(256), BigInteger.ZERO, BigInteger.valueOf(-1)));
        input.setBigDecimalSet(orderedSet(new BigDecimal("1.2300"), BigDecimal.ZERO, new BigDecimal("-10.5")));
        var decoded = roundtrip(input);

        assertEquals(input.getIntSet(), decoded.getIntSet());
        assertEquals(input.getLongSet(), decoded.getLongSet());
        assertEquals(input.getStringSet(), decoded.getStringSet());
        assertEquals(input.getBlobSet(), decoded.getBlobSet());
        assertEquals(input.getBigIntegerSet(), decoded.getBigIntegerSet());
        assertEquals(input.getBigDecimalSet(), decoded.getBigDecimalSet());
    }

    @Test
    void emptyAndAbsentSets() {
        var input = base();
        input.setIntSet(Set.of());
        var decoded = roundtrip(input);
        assertEquals(Set.of(), decoded.getIntSet());
        assertEquals(null, decoded.getStringSet());
    }

    @Test
    void decodePreservesWireOrderAndReencodesByteIdentically() {
        var input = base();
        input.setIntSet(orderedSet(9, 4, 7));
        input.setStringSet(orderedSet("z", "a"));
        byte[] first = encode(input);

        var decoded = new NestedCollectionsInput();
        decoded.decodeFrom(new SparrowhawkDeserializer(first));
        byte[] second = encode(decoded);
        assertEquals(HexFormat.of().formatHex(first), HexFormat.of().formatHex(second));

        assertEquals(List.copyOf(orderedSet(9, 4, 7)), List.copyOf(decoded.getIntSet()));
        byte[] third = encode(decoded);
        assertEquals(HexFormat.of().formatHex(first), HexFormat.of().formatHex(third));
    }

    @Test
    void setEncodesIdenticallyToListWithSameElements() {
        var set = IntSet$Set.fromList(orderedSet(1, 2));
        var s = new SparrowhawkSerializer(new byte[3]);
        set.encodeTo(s);
        assertEquals("270509", HexFormat.of().formatHex(s.payload()));
    }

    @Test
    void duplicateWireElementsAreRejected() {
        var set = new IntSet$Set();
        var d = new SparrowhawkDeserializer(new byte[]{0x27, 0x05, 0x05});
        assertThrows(ParseException.class, () -> set.decodeFrom(d));
    }

    @Test
    void setsNestedInCollections() {
        var input = base();
        input.setIntSetList(List.of(orderedSet(1, 2), Set.of(), orderedSet(3)));
        Map<String, Set<String>> m = new LinkedHashMap<>();
        m.put("a", orderedSet("x", "y"));
        m.put("b", Set.of());
        input.setStringSetMap(m);
        var decoded = roundtrip(input);

        assertEquals(input.getIntSetList(), decoded.getIntSetList());
        assertEquals(m, decoded.getStringSetMap());
    }

    @Test
    void sparseListOfSets() {
        var input = base();
        input.setSparseIntSetList(Arrays.asList(orderedSet(1), null, Set.of()));
        var decoded = roundtrip(input);
        assertEquals(Arrays.asList(orderedSet(1), null, Set.of()), decoded.getSparseIntSetList());
    }

    @Test
    void gettersMemoize() {
        var input = base();
        input.setIntSet(orderedSet(5));
        var decoded = roundtrip(input);
        assertSame(decoded.getIntSet(), decoded.getIntSet());
    }

    @Test
    void identicalSetDeclarationsShareGeneratedFlyweight() throws ReflectiveOperationException {
        var input = base();
        input.setIntSet(orderedSet(1));
        input.setDuplicateIntSet(orderedSet(2));
        input.size();

        assertSame(storedField(input, "intSet").getClass(), storedField(input, "duplicateIntSet").getClass());
    }

    private static Object storedField(NestedCollectionsInput input, String name) throws ReflectiveOperationException {
        var field = NestedCollectionsInput.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(input);
    }
}
