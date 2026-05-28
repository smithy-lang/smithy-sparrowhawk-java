/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;


public class SparrowhawkDeserializerTest {
    @Test
    public void skipDeeplyNestedListsThrowsParseException() {
        byte[] payload = new byte[130];
        Arrays.fill(payload, (byte) 0x13);

        SparrowhawkDeserializer d = new SparrowhawkDeserializer(payload);
        long fieldset = 1L << 3; // one unknown LIST-typed field

        assertThrows(ParseException.class, () -> d.skipRemaining(fieldset, KConstants.T_LIST));
    }
}
