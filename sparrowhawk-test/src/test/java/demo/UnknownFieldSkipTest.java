/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkDeserializer;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer;

class UnknownFieldSkipTest {

    @Test
    void skipsUnknownNestedCollectionFields() {
        var input = new NestedCollectionsInput();
        input.setIntListList(List.of(List.of(1, 2), List.of()));
        input.setStringListList(List.of(List.of("a"), List.of()));
        input.setStructListList(List.of(List.of(struct("s", List.of(3)))));
        input.setBlobListList(List.of(List.of(ByteBuffer.wrap("b".getBytes(StandardCharsets.UTF_8)))));
        input.setTimestampListList(List.of(List.of(new Date(1234))));
        input.setIntListListListList(List.of(List.of(List.of(List.of(4)))));
        input.setStringMapList(List.of(orderedMap("k", "v")));
        input.setLongListMap(orderedMap("l", List.of(5L)));
        input.setIntListMapMap(orderedMap("m", orderedMap("n", List.of(6))));
        input.setSparseIntListList(Arrays.asList(null, List.of(7)));
        input.setSparseStringListMap(orderedMap("s", null, "t", List.of("u")));
        input.setRequiredIntListList(List.of(List.of(8)));

        var s = new SparrowhawkSerializer(input.size());
        input.encodeTo(s);
        byte[] payload = s.payload();

        var d = new SparrowhawkDeserializer(payload);
        var skipped = new SkipTarget();
        skipped.decodeFrom(d);
        assertEquals(List.of(List.of(1, 2), List.of()), skipped.getIntListList());
        d.done();
    }

    private static NestedStructure struct(String s, List<Integer> ints) {
        var n = new NestedStructure();
        n.setInnerStr(s);
        n.setList(ints);
        return n;
    }

    private static <K, V> Map<K, V> orderedMap(Object... kvs) {
        Map<K, V> m = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            m.put((K) kvs[i], (V) kvs[i + 1]);
        }
        return m;
    }
}
