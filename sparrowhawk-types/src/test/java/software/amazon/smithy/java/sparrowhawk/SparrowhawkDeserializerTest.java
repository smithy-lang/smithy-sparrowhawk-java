/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;


public class SparrowhawkDeserializerTest {
    private static final byte[] HELLO = new byte[]{0x15, 'h', 'e', 'l', 'l', 'o'};

    @Test
    public void skipDeeplyNestedListsThrowsParseException() {
        byte[] payload = new byte[130];
        Arrays.fill(payload, (byte) 0x13);

        SparrowhawkDeserializer d = new SparrowhawkDeserializer(payload);
        long fieldset = 1L << 3; // one unknown LIST-typed field

        assertThrows(ParseException.class, () -> d.skipRemaining(fieldset, KConstants.T_LIST));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("boundedInputs")
    public void respectsInputBounds(String name, SparrowhawkDeserializer d) {
        assertEquals(HELLO.length, d.remaining());
        assertEquals("hello", d.string());
        assertEquals(0, d.remaining());
        d.done();
    }

    @ParameterizedTest
    @ValueSource(ints = {7, Integer.MAX_VALUE})
    public void rejectsImpossibleElementCounts(int count) {
        SparrowhawkDeserializer d = new SparrowhawkDeserializer(HELLO);
        assertThrows(ParseException.class, () -> d.checkElementCount(count));
    }

    @Test
    public void checksElementCountAgainstRemainingBytes() {
        SparrowhawkDeserializer d = new SparrowhawkDeserializer(HELLO);
        d.checkElementCount(HELLO.length);
        d.string();
        assertThrows(ParseException.class, () -> d.checkElementCount(1));
    }

    private static Stream<Arguments> boundedInputs() {
        byte[] padded = new byte[HELLO.length + 8];
        System.arraycopy(HELLO, 0, padded, 4, HELLO.length);
        ByteBuffer buffer = ByteBuffer.wrap(padded);
        buffer.position(4);
        buffer.limit(4 + HELLO.length);

        return Stream.of(
            Arguments.of("byte array", new SparrowhawkDeserializer(HELLO)),
            Arguments.of("byte array range", new SparrowhawkDeserializer(padded, 4, HELLO.length)),
            Arguments.of("byte buffer", new SparrowhawkDeserializer(buffer)),
            Arguments.of("sliced byte buffer", new SparrowhawkDeserializer(buffer.slice()))
        );
    }
}
