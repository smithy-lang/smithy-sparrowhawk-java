/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.sparrowhawk.codegen.Enhancer;

class EnhancerNestedTest {
    private static final Model MODEL = new ModelAssembler()
        .addImport(Paths.get(System.getProperty("user.dir"), "model"))
        .discoverModels()
        .assemble()
        .unwrap();

    @Test
    void enhanceNestedAndSparsePayload() {
        var input = new NestedCollectionsInput();
        input.setIntListList(List.of(List.of(1, 2), List.of()));
        input.setStringListList(List.of(List.of("a")));
        input.setIntListListListList(List.of(List.of(List.of(List.of(3)))));
        input.setStringMapList(List.of(orderedMap("k", "v")));
        input.setIntListMapMap(orderedMap("m", orderedMap("n", List.of(4))));
        input.setSparseIntListList(Arrays.asList(null, List.of(5)));
        input.setSparseStringListMap(orderedMap("s", null, "t", List.of("u")));
        input.setListOfSparseIntList(List.of(Arrays.asList(6, null)));
        input.setBigIntegerList(List.of(BigInteger.ONE.shiftLeft(128)));
        input.setBigDecimalMap(orderedMap("decimal", new BigDecimal("123.4500")));
        input.setSparseBigIntegerList(Arrays.asList(null, BigInteger.TEN));
        input.setRequiredIntListList(List.of());

        var s = new SparrowhawkSerializer(input.size());
        input.encodeTo(s);

        var out = new ByteArrayOutputStream();
        new Enhancer(MODEL, new PrintStream(out, true, StandardCharsets.UTF_8)).enhance(
            s.payload(),
            MODEL.expectShape(ShapeId.from("demo#NestedCollectionsInput"))
        );
        String dump = out.toString(StandardCharsets.UTF_8);
        assertTrue(dump.contains("null"), dump);
        assertTrue(dump.contains(BigInteger.ONE.shiftLeft(128).toString()), dump);
        assertTrue(dump.contains("123.4500"), dump);
        assertTrue(!dump.contains("[ERROR"), dump);
    }

    private static <K, V> Map<K, V> orderedMap(Object... kvs) {
        Map<K, V> m = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            m.put((K) kvs[i], (V) kvs[i + 1]);
        }
        return m;
    }
}
