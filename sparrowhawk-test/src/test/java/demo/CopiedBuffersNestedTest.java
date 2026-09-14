/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkDeserializer;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer;

class CopiedBuffersNestedTest {

    private static ByteBuffer bb(String s) {
        return ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void crossDecodeBetweenProjections() {
        var input = new NestedCollectionsInput();
        input.setBlobListList(List.of(List.of(bb("one"), bb("")), List.of()));
        Map<String, List<ByteBuffer>> blobMap = new LinkedHashMap<>();
        blobMap.put("k", List.of(bb("two")));
        input.setBlobListMap(blobMap);
        input.setIntListList(List.of(List.of(1)));
        input.setRequiredIntListList(List.of());

        var s = new SparrowhawkSerializer(input.size());
        input.encodeTo(s);
        byte[] payload = s.payload();

        var copied = new CbNestedCollectionsInput();
        copied.decodeFrom(new SparrowhawkDeserializer(payload));
        assertEquals(List.of(List.of(bb("one"), bb("")), List.of()), copied.getBlobListList());
        assertEquals(blobMap, copied.getBlobListMap());
        assertEquals(List.of(List.of(1)), copied.getIntListList());

        var s2 = new SparrowhawkSerializer(copied.size());
        copied.encodeTo(s2);
        var back = new NestedCollectionsInput();
        back.decodeFrom(new SparrowhawkDeserializer(s2.payload()));
        assertEquals(input.getBlobListList(), back.getBlobListList());
        assertEquals(input.getBlobListMap(), back.getBlobListMap());
    }

    @Test
    void crossNamespaceFlyweightRoundtrip() {
        var input = new NestedCollectionsInput();
        input.setCrossNs(List.of(List.of(1, 2), List.of()));
        input.setRequiredIntListList(List.of());

        var s = new SparrowhawkSerializer(input.size());
        input.encodeTo(s);
        var decoded = new NestedCollectionsInput();
        decoded.decodeFrom(new SparrowhawkDeserializer(s.payload()));
        assertEquals(List.of(List.of(1, 2), List.of()), decoded.getCrossNs());
    }
}
