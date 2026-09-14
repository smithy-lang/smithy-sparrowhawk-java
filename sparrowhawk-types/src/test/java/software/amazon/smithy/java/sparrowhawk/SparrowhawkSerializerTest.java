/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.byteListLengthEncodedSize;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class SparrowhawkSerializerTest {
    @Test
    public void writesObjectBackedStringsAndBigIntegers() {
        String string = "hello";
        BigInteger integer = new BigInteger("12345678901234567890");
        int size = byteListLengthEncodedSize(string.getBytes(StandardCharsets.UTF_8).length)
            + byteListLengthEncodedSize(integer.toByteArray().length);
        SparrowhawkSerializer serializer = new SparrowhawkSerializer(new byte[size]);
        serializer.writeString((Object) string);
        serializer.writeBigInteger((Object) integer);

        SparrowhawkDeserializer deserializer = new SparrowhawkDeserializer(serializer.payload());
        assertEquals(string, deserializer.string());
        assertEquals(integer, deserializer.bigInteger());
    }
}
