/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkDeserializer;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkObject;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer;

class GoldenBytesTest {
    private static String encode(SparrowhawkObject o) {
        var s = new SparrowhawkSerializer(o.size());
        o.encodeTo(s);
        return HexFormat.of().formatHex(s.payload());
    }

    private static <K, V> Map<K, V> orderedMap(Object... kvs) {
        Map<K, V> m = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            m.put((K) kvs[i], (V) kvs[i + 1]);
        }
        return m;
    }

    @Test
    void stringMapMap() {
        var input = new MapCollectionsInput();
        input.setStringMapMap(
            orderedMap(
                "a",
                orderedMap("x", "1"),
                "b",
                orderedMap()
            )
        );
        String hex = encode(input);
        assertEquals("491141312305610562231d3113057813053101", hex);
        var decoded = new MapCollectionsInput();
        decoded.decodeFrom(new SparrowhawkDeserializer(HexFormat.of().parseHex(hex)));
        assertEquals(input.getStringMapMap(), decoded.getStringMapMap());
    }

    @Test
    void intMapMapMap() {
        var input = new MapCollectionsInput();
        input.setIntMapMapMap(
            orderedMap(
                "k",
                orderedMap("l", orderedMap("m", 1, "n", 2))
            )
        );
        String hex = encode(input);
        assertEquals("5d21553113056b133d3113056c13253123056d056e270509", hex);
        var decoded = new MapCollectionsInput();
        decoded.decodeFrom(new SparrowhawkDeserializer(HexFormat.of().parseHex(hex)));
        assertEquals(input.getIntMapMapMap(), decoded.getIntMapMapMap());
    }

    @Test
    void structMapMap() {
        var nested = new NestedStructure();
        nested.setInnerStr("i");
        nested.setList(List.of(1, 2));
        var input = new MapCollectionsInput();
        input.setStructMapMap(orderedMap("s", orderedMap("t", nested)));
        String hex = encode(input);
        assertEquals("514149311305731331311305741319310569270509", hex);
        var decoded = new MapCollectionsInput();
        decoded.decodeFrom(new SparrowhawkDeserializer(HexFormat.of().parseHex(hex)));
        assertEquals(input.getStructMapMap(), decoded.getStructMapMap());
    }

    @Test
    void nestedIntListGolden() {
        String golden = "1d1123170527090d";
        var input = new GoldenNestedList();
        input.setNested(List.of(List.of(1), List.of(2, 3)));
        assertEquals(golden, encode(input));

        var decoded = new GoldenNestedList();
        decoded.decodeFrom(new SparrowhawkDeserializer(HexFormat.of().parseHex(golden)));
        assertEquals(List.of(List.of(1), List.of(2, 3)), decoded.getNested());
    }

    @Test
    void intListMap() {
        var input = new MapCollectionsInput();
        input.setIntListMap(orderedMap("p", List.of(1, 2, 3), "q", List.of()));
        String hex = encode(input);
        assertEquals("398131312305700571233705090d07", hex);
        var decoded = new MapCollectionsInput();
        decoded.decodeFrom(new SparrowhawkDeserializer(HexFormat.of().parseHex(hex)));
        assertEquals(input.getIntListMap(), decoded.getIntListMap());
    }
}
