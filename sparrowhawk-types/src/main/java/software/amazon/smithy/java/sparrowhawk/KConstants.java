/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

public final class KConstants {
    public static final int T_LIST = 0;
    public static final int T_VARINT = 1;
    public static final int T_FOUR = 2;
    public static final int T_EIGHT = 3;

    public static final int LIST_BYTES = 0;
    public static final int LIST_LEN_DELIMITED_ITEMS = 0b001;
    public static final int LIST_VARINTS = 0b011;
    public static final int LIST_FOUR = 0b101;
    public static final int LIST_EIGHT = 0b111;

    public static String fieldType(long fieldset) {
        switch ((int) (fieldset & 3)) {
            case 0:
                return "T_LIST";
            case 1:
                return "T_VARINT";
            case 2:
                return "T_FOUR";
            case 3:
                return "T_EIGHT";
            default:
                throw new IllegalStateException("impossible");
        }
    }

    public static long varintField(long fields) {
        return (fields << 3) + T_VARINT;
    }

    public static long fourField(long fields) {
        return (fields << 3) + T_FOUR;
    }

    public static long eightField(long fields) {
        return (fields << 3) + T_EIGHT;
    }

    public static long listField(long fields) {
        return (fields << 3);
    }

    public static String listType(int len) {
        if (isByteListLength(len)) return "byte";
        int lt = len & 7;
        switch (lt) {
            case LIST_LEN_DELIMITED_ITEMS:
                return "length-delimited items";
            case LIST_VARINTS:
                return "varints";
            case LIST_FOUR:
                return "four-byte elements";
            case LIST_EIGHT:
                return "eight-byte elements";
            default:
                throw new IllegalArgumentException("invalid list type: " + lt);
        }
    }

    public static long encodeByteListLength(long l) {
        return l << 1;
    }

    public static int encodeLenPrefixedListLength(int l) {
        return (l << 3) + LIST_LEN_DELIMITED_ITEMS;
    }

    public static int encodeVarintListLength(int l) {
        return (l << 3) + LIST_VARINTS;
    }

    public static long encodeFourBListLength(long l) {
        return (l << 3) + LIST_FOUR;
    }

    public static long encodeEightBListLength(long l) {
        return (l << 3) + LIST_EIGHT;
    }

    public static boolean isByteListLength(long l) {
        return (l & 1) == 0;
    }

    public static long decodeElementCount(long l) {
        return l >> (1 + (2 * (l & 1)));
//        if ((l & 1) == 0) return l >> 1;
//        return l >> 3;
    }

    public static int decodeByteListLength(long l) {
        return (int) (l >> 1);
    }

    public static int decodeByteListLengthChecked(long len) {
        long type = len & 1;
        if (type != LIST_BYTES) {
            badList("bytes", type);
        }
        return (int) len >> 1;
    }

    public static int decodeLenPrefixedListLengthChecked(long len) {
        long type = len & 7;
        if (type != LIST_LEN_DELIMITED_ITEMS) {
            badList("length-delimited", type);
        }
        return (int) len >> 3;
    }

    public static int decodeVarintListLengthChecked(long len) {
        long type = len & 7;
        if (type != LIST_VARINTS) {
            badList("varint", type);
        }
        return (int) len >> 3;
    }

    public static int decodeFourByteListLengthChecked(long len) {
        long type = len & 7;
        if (type != LIST_FOUR) {
            badList("four-byte", type);
        }
        return (int) len >> 3;
    }

    public static int decodeEightByteListLengthChecked(long len) {
        long type = len & 7;
        if (type != LIST_EIGHT) {
            badList("eight-byte", type);
        }
        return (int) len >> 3;
    }

    private static void badList(String expected, long type) {
        throw new RuntimeException(
            "expected list of " + expected + " items, got: "
                + listType((int) type)
        );
    }
}
