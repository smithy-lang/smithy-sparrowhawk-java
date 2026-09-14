/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.lenPrefixedListLengthEncodedSize;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;


public class StringListSizeTest {
    @Test
    public void sizeSurvivesDecode() {
        StringList original = StringList.fromList(List.of("one", "two", "three"));
        int expectedSize = original.size();

        StringList decoded = roundtrip(original);
        assertEquals(expectedSize, decoded.size());
        assertEquals(List.of("one", "two", "three"), decoded.toList());

        StringList again = roundtrip(decoded);
        assertEquals(List.of("one", "two", "three"), again.toList());
    }

    @Test
    public void emptySizeSurvivesDecode() {
        StringList original = StringList.fromList(List.of());
        StringList decoded = roundtrip(original);
        assertEquals(0, decoded.size());
        assertEquals(List.of(), decoded.toList());
    }

    @Test
    public void sparseSizeSurvivesDecode() {
        List<String> strings = Arrays.asList("one", null, "three");
        SparseStringList original = SparseStringList.fromList(strings);
        int expectedSize = original.size();

        SparseStringList decoded = roundtripSparse(original);
        assertEquals(expectedSize, decoded.size());
        assertEquals(strings, decoded.toList());

        SparseStringList again = roundtripSparse(decoded);
        assertEquals(strings, again.toList());
    }

    private static StringList roundtrip(StringList list) {
        SparrowhawkSerializer s = new SparrowhawkSerializer(
            new byte[lenPrefixedListLengthEncodedSize(list.size(), list.elementCount())]
        );
        list.encodeTo(s);
        StringList decoded = new StringList();
        decoded.decodeFrom(new SparrowhawkDeserializer(s.payload()));
        return decoded;
    }

    private static SparseStringList roundtripSparse(SparseStringList list) {
        SparrowhawkSerializer s = new SparrowhawkSerializer(
            new byte[lenPrefixedListLengthEncodedSize(list.size(), list.elementCount())]
        );
        list.encodeTo(s);
        SparseStringList decoded = new SparseStringList();
        decoded.decodeFrom(new SparrowhawkDeserializer(s.payload()));
        return decoded;
    }
}
