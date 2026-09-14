/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package demo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkDeserializer;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkObject;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer;

class BigNumberCollectionsTest {

    private record RoundtripCase(
        String name,
        Consumer<NestedCollectionsInput> configure,
        Function<NestedCollectionsInput, ?> read,
        Object expected
    ) {
        @Override
        public String toString() {
            return name;
        }
    }

    private record ReencodeCase(String name, Consumer<NestedCollectionsInput> configure) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static byte[] encode(SparrowhawkObject value) {
        var serializer = new SparrowhawkSerializer(value.size());
        value.encodeTo(serializer);
        return serializer.payload();
    }

    private static <V> Map<String, V> orderedMap(Object... keyValues) {
        Map<String, V> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], (V) keyValues[i + 1]);
        }
        return map;
    }

    private static NestedCollectionsInput base() {
        var input = new NestedCollectionsInput();
        input.setRequiredIntListList(List.of());
        return input;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("roundtripCases")
    void roundtripCollections(RoundtripCase testCase) {
        var input = base();
        testCase.configure().accept(input);
        byte[] encoded = encode(input);

        var decoded = new NestedCollectionsInput();
        decoded.decodeFrom(new SparrowhawkDeserializer(encoded));

        assertEquals(testCase.expected(), testCase.read().apply(decoded));
    }

    private static Stream<RoundtripCase> roundtripCases() {
        BigInteger huge = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);
        BigDecimal precise = new BigDecimal("12345678901234567890.000000000123456789");
        return Stream.of(
            roundtripCase(
                "dense bigInteger list",
                List.of(BigInteger.ZERO, BigInteger.valueOf(-1), huge),
                NestedCollectionsInput::setBigIntegerList,
                NestedCollectionsInput::getBigIntegerList
            ),
            roundtripCase(
                "dense bigDecimal list",
                List.of(BigDecimal.ZERO, new BigDecimal("-1.25"), precise),
                NestedCollectionsInput::setBigDecimalList,
                NestedCollectionsInput::getBigDecimalList
            ),
            roundtripCase(
                "dense bigInteger map",
                orderedMap("zero", BigInteger.ZERO, "huge", huge),
                NestedCollectionsInput::setBigIntegerMap,
                NestedCollectionsInput::getBigIntegerMap
            ),
            roundtripCase(
                "dense bigDecimal map",
                orderedMap("negative", new BigDecimal("-10.500"), "precise", precise),
                NestedCollectionsInput::setBigDecimalMap,
                NestedCollectionsInput::getBigDecimalMap
            ),
            roundtripCase(
                "nested bigInteger list",
                List.of(List.of(huge), List.of(), List.of(BigInteger.TEN)),
                NestedCollectionsInput::setBigIntegerListList,
                NestedCollectionsInput::getBigIntegerListList
            ),
            roundtripCase(
                "nested bigDecimal map",
                List.of(orderedMap("first", precise), orderedMap(), orderedMap("last", new BigDecimal("0.00"))),
                NestedCollectionsInput::setBigDecimalMapList,
                NestedCollectionsInput::getBigDecimalMapList
            ),
            roundtripCase(
                "sparse bigInteger list",
                Arrays.asList(BigInteger.ONE, null, BigInteger.TEN.negate()),
                NestedCollectionsInput::setSparseBigIntegerList,
                NestedCollectionsInput::getSparseBigIntegerList
            ),
            roundtripCase(
                "sparse bigDecimal map",
                orderedMap("present", new BigDecimal("1.2300"), "absent", null, "zero", new BigDecimal("0.00")),
                NestedCollectionsInput::setSparseBigDecimalMap,
                NestedCollectionsInput::getSparseBigDecimalMap
            ),
            roundtripCase(
                "sparse bigInteger map",
                orderedMap("present", BigInteger.TEN, "absent", null),
                NestedCollectionsInput::setSparseBigIntegerMap,
                NestedCollectionsInput::getSparseBigIntegerMap
            ),
            roundtripCase(
                "sparse bigDecimal list",
                Arrays.asList(new BigDecimal("1.2300"), null, new BigDecimal("0.00")),
                NestedCollectionsInput::setSparseBigDecimalList,
                NestedCollectionsInput::getSparseBigDecimalList
            ),
            roundtripCase(
                "nested sparse bigInteger list",
                List.of(Arrays.asList(BigInteger.ONE, null), List.of(), Arrays.asList((BigInteger) null)),
                NestedCollectionsInput::setListOfSparseBigIntegerList,
                NestedCollectionsInput::getListOfSparseBigIntegerList
            )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reencodeCases")
    void decodeThenReencodeIsByteIdentical(ReencodeCase testCase) {
        var input = base();
        testCase.configure().accept(input);
        byte[] encoded = encode(input);

        var decoded = new NestedCollectionsInput();
        decoded.decodeFrom(new SparrowhawkDeserializer(encoded));

        assertArrayEquals(encoded, encode(decoded));
    }

    private static Stream<ReencodeCase> reencodeCases() {
        return Stream.of(
            new ReencodeCase(
                "bigInteger list",
                input -> input.setBigIntegerList(List.of(BigInteger.ONE.shiftLeft(130)))
            ),
            new ReencodeCase(
                "bigDecimal map",
                input -> input.setBigDecimalMap(orderedMap("value", new BigDecimal("-987654321.000001")))
            ),
            new ReencodeCase(
                "sparse bigInteger list",
                input -> input.setSparseBigIntegerList(Arrays.asList(null, BigInteger.valueOf(-42)))
            )
        );
    }

    private static <T> RoundtripCase roundtripCase(
        String name,
        T expected,
        BiConsumer<NestedCollectionsInput, T> write,
        Function<NestedCollectionsInput, T> read
    ) {
        return new RoundtripCase(name, input -> write.accept(input, expected), read, expected);
    }
}
