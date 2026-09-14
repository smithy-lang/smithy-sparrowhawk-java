/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkDeserializer;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkObject;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer;

class SparseNestedCollectionsTest {

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

    private static <K, V> Map<K, V> orderedMap(Object... kvs) {
        Map<K, V> m = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            m.put((K) kvs[i], (V) kvs[i + 1]);
        }
        return m;
    }

    @Test
    void sparseGoldenVector() {
        String golden = "1d1123010d111705";
        var input = new GoldenSparseNestedList();
        input.setNested(Arrays.asList(null, List.of(1)));
        var s = new SparrowhawkSerializer(input.size());
        input.encodeTo(s);
        assertEquals(golden, HexFormat.of().formatHex(s.payload()));

        var decoded = new GoldenSparseNestedList();
        decoded.decodeFrom(new SparrowhawkDeserializer(HexFormat.of().parseHex(golden)));
        assertEquals(Arrays.asList(null, List.of(1)), decoded.getNested());
    }

    @Test
    void sparseIntListListNullPlacements() {
        List<List<List<Integer>>> cases = List.of();
        for (List<List<Integer>> value : Arrays.<List<List<Integer>>>asList(
            Arrays.asList((List<Integer>) null),
            Arrays.asList(null, List.of(1, 2)),
            Arrays.asList(List.of(1, 2), null),
            Arrays.asList(List.of(1), null, List.of(2, 3), null),
            Arrays.asList(null, null, null),
            Arrays.asList(List.of(), null, List.of()),
            List.of()
        )) {
            var input = base();
            input.setSparseIntListList(value);
            assertEquals(value, roundtrip(input).getSparseIntListList());
        }
    }

    @Test
    void denseListOfSparseLeafLists() {
        var input = base();
        input.setListOfSparseIntList(
            List.of(
                Arrays.asList(1, null, 3),
                Arrays.asList((Integer) null),
                List.of()
            )
        );
        assertEquals(
            List.of(Arrays.asList(1, null, 3), Arrays.asList((Integer) null), List.of()),
            roundtrip(input).getListOfSparseIntList()
        );
    }

    @Test
    void sparseStringMapList() {
        var input = base();
        input.setSparseStringMapList(
            Arrays.asList(
                orderedMap("a", "b"),
                null,
                orderedMap()
            )
        );
        assertEquals(
            Arrays.asList(orderedMap("a", "b"), null, orderedMap()),
            roundtrip(input).getSparseStringMapList()
        );
    }

    @Test
    void sparseStringListMap() {
        var input = base();
        Map<String, List<String>> m = orderedMap(
            "present",
            List.of("x", ""),
            "absent",
            null,
            "empty",
            List.of()
        );
        input.setSparseStringListMap(m);
        assertEquals(m, roundtrip(input).getSparseStringListMap());
    }

    @Test
    void sparseMapOfMap() {
        var input = base();
        Map<String, Map<String, String>> m = orderedMap(
            "a",
            orderedMap("x", "1"),
            "b",
            null,
            "c",
            orderedMap()
        );
        input.setSparseStringMapMap(m);
        assertEquals(m, roundtrip(input).getSparseStringMapMap());
    }

    @Test
    void sparseDeepMapOfMapOfMap() {
        var input = base();
        Map<String, Map<String, Map<String, Integer>>> m = orderedMap(
            "k",
            orderedMap("l", orderedMap("m", 1)),
            "n",
            null
        );
        input.setSparseIntMapMapMap(m);
        assertEquals(m, roundtrip(input).getSparseIntMapMapMap());
    }

    @Test
    void sparseReencodeAfterDecodeIsByteIdentical() {
        var input = base();
        input.setSparseIntListList(Arrays.asList(null, List.of(1, 2), null, List.of()));
        input.setSparseStringListMap(orderedMap("a", List.of("z"), "b", null));
        byte[] first = encode(input);
        var decoded = new NestedCollectionsInput();
        decoded.decodeFrom(new SparrowhawkDeserializer(first));
        byte[] second = encode(decoded);
        assertEquals(HexFormat.of().formatHex(first), HexFormat.of().formatHex(second));
    }

    @Test
    void sparseSizeRecomputeAfterDecodeAndMutation() {
        var input = base();
        input.setSparseIntListList(Arrays.asList(null, List.of(7)));
        var decoded = new NestedCollectionsInput();
        decoded.decodeFrom(new SparrowhawkDeserializer(encode(input)));
        decoded.setRequiredIntListList(List.of(List.of(1)));
        var again = roundtrip(decoded);
        assertEquals(Arrays.asList(null, List.of(7)), again.getSparseIntListList());
        assertEquals(List.of(List.of(1)), again.getRequiredIntListList());
    }
}
