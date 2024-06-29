/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.lenPrefixedListLengthEncodedSize;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;


public class SparseStringListTest {
    @Test
    public void sparse() {
        List<String> strings = new ArrayList<>();
        strings.add("one");
        strings.add(null);
        strings.add("three");
        strings.add("");

        SparseStringList sparse = SparseStringList.fromList(strings);
        SparseStringList roundtrip = roundtrip(sparse);

        assertEquals(strings, roundtrip.toList());
    }

    @Test
    public void oneNull() {
        List<String> strings = new ArrayList<>();
        strings.add(null);

        SparseStringList sparse = SparseStringList.fromList(strings);
        SparseStringList roundtrip = roundtrip(sparse);

        assertEquals(strings, roundtrip.toList());
    }

    @Test
    public void noNulls() {
        List<String> strings = new ArrayList<>();
        strings.add("one");
        strings.add("three");
        strings.add("");

        SparseStringList sparse = SparseStringList.fromList(strings);
        SparseStringList roundtrip = roundtrip(sparse);

        assertEquals(strings, roundtrip.toList());
    }

    @Test
    public void bigList() {
        byte[] bytes = new byte[1024];
        Arrays.fill(bytes, (byte) 'A');
        String bigString = new String(bytes, StandardCharsets.UTF_8);
        List<String> strings = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            if (i % 1024 == 0) {
                strings.add(null);
            } else {
                strings.add(i + ":" + bigString);
            }
        }

        SparseStringList sparse = SparseStringList.fromList(strings);
        SparseStringList roundtrip = roundtrip(sparse);

        assertEquals(strings, roundtrip.toList());
    }

    private static SparseStringList roundtrip(SparseStringList source) {
        byte[] payload = new byte[lenPrefixedListLengthEncodedSize(source.size(), source.elementCount())];
        SparrowhawkSerializer s = new SparrowhawkSerializer(payload);
        source.encodeTo(s);

        SparseStringList roundtrip = new SparseStringList();
        SparrowhawkDeserializer d = new SparrowhawkDeserializer(s.payload());
        roundtrip.decodeFrom(d);
        return roundtrip;
    }
}
