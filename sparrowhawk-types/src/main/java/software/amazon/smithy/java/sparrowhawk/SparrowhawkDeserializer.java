/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public final class SparrowhawkDeserializer {
    private final byte[] b;
    private final int len;
    private int pos;

    public SparrowhawkDeserializer(byte[] b) {
        this.b = b;
        this.len = b.length;
    }

    public SparrowhawkDeserializer(byte[] b, int off, int len) {
        this.b = b;
        this.pos = off;
        this.len = len;
    }

    public SparrowhawkDeserializer(ByteBuffer b) {
        if (b.hasArray()) {
            this.b = b.array();
            this.pos = b.position() + b.arrayOffset();
            this.len = b.remaining();
        } else {
            byte[] bytes = bytes(b);
            this.b = bytes;
            this.len = bytes.length;
        }
    }

    private static byte[] bytes(ByteBuffer b) {
        byte[] bytes = new byte[b.remaining()];
        b.get(bytes);
        return bytes;
    }

    public int pos() {
        return pos;
    }

    public void done() {
        if (pos != len) {
            throw new RuntimeException("still has " + (len - pos) + "bytes");
        }
    }

    public String string() {
        int len = (int) varUL();
        if (!KConstants.isByteListLength(len)) {
            throw new RuntimeException("not bytes: " + listType(len));
        }
        int decodedLen = decodeByteListLength(len);
        String s = new String(b, pos, decodedLen, StandardCharsets.UTF_8);
        pos += decodedLen;
        return s;
    }

    public ByteBuffer bytes() {
        int len = (int) varUL();
        if (!KConstants.isByteListLength(len)) {
            throw new RuntimeException("not bytes: " + listType(len));
        }
        int decodedLen = decodeByteListLength(len);
        ByteBuffer bb = ByteBuffer.wrap(b, pos, decodedLen).slice();
        pos += decodedLen;
        return bb;
    }

    public ByteBuffer object() {
        int start = pos;
        int len = (int) varUL();
        int prefix = pos - start;
        if (!KConstants.isByteListLength(len)) {
            throw new RuntimeException("not bytes: " + listType(len));
        }
        int decodedLen = decodeByteListLength(len);
        ByteBuffer bb = ByteBuffer.wrap(b, start, decodedLen + prefix).slice();
        pos += decodedLen;
        return bb;
    }

    public float f4() {
        float f = Float.intBitsToFloat(read4(b, pos));
        pos += 4;
        return f;
    }

    public double d8() {
        double d = Double.longBitsToDouble(read8(b, pos));
        pos += 8;
        return d;
    }

    public Date date() {
        return new Date(Math.round(d8() * 1000));
    }

    public Instant instant() {
        return Instant.ofEpochMilli(Math.round(d8() * 1000));
    }

    public boolean bool() {
        return varUL() != 0;
    }

    public byte varB() {
        return (byte) varI();
    }

    public short varS() {
        return (short) varI();
    }

    public int varI() {
        return zigzag4(varUI());
    }

    private static int zigzag4(int i) {
        return (i >>> 1) ^ -(i & 1);
    }

    public int varUI() {
        return (int) varUL();
    }

    public long varL() {
        return zigzag8(varUL());
    }

    private static long zigzag8(long i) {
        return (i >>> 1) ^ -(i & 1L);
    }

    public long varUL() {
        int f = b[pos++] & 0xFF;
        if ((f & 1) == 1) {
            return (f >> 1);
        }

        int len = 1 + Integer.numberOfTrailingZeros(f | (1 << 8));
        if (len == 9) {
            long v = read8(b, pos);
            pos += 8;
            return v;
        }

        long acc = f >> len;
        for (int i = 1; i < len; i++) {
            long update = ((long) (b[pos++] & 0xFF)) << ((8 * i) - len);
            acc |= update;
        }
        return acc;
    }

    public List<Byte> decodeByteList() {
        int sz = decodeVarintListLengthChecked(varUL());
        Byte[] bytes = new Byte[sz];
        for (int i = 0; i < sz; i++) {
            bytes[i] = varB();
        }
        return Arrays.asList(bytes);
    }

    public List<Short> decodeShortList() {
        int sz = decodeVarintListLengthChecked(varUL());
        Short[] shorts = new Short[sz];
        for (int i = 0; i < sz; i++) {
            shorts[i] = varS();
        }
        return Arrays.asList(shorts);
    }

    public List<Integer> decodeIntegerList() {
        int sz = decodeVarintListLengthChecked(varUL());
        Integer[] integers = new Integer[sz];
        for (int i = 0; i < sz; i++) {
            integers[i] = varI();
        }
        return Arrays.asList(integers);
    }

    public List<Long> decodeLongList() {
        int sz = decodeVarintListLengthChecked(varUL());
        Long[] longs = new Long[sz];
        for (int i = 0; i < sz; i++) {
            longs[i] = varL();
        }
        return Arrays.asList(longs);
    }

    public List<Double> decodeDoubleList() {
        int sz = decodeEightByteListLengthChecked(varUL());
        Double[] doubles = new Double[sz];
        for (int i = 0; i < sz; i++) {
            doubles[i] = d8();
        }
        return Arrays.asList(doubles);
    }

    public List<Float> decodeFloatList() {
        int sz = decodeFourByteListLengthChecked(varUI());
        Float[] floats = new Float[sz];
        for (int i = 0; i < sz; i++) {
            floats[i] = f4();
        }
        return Arrays.asList(floats);
    }

    public List<Boolean> decodeBooleanList() {
        int sz = decodeVarintListLengthChecked(varUL());
        Boolean[] booleans = new Boolean[sz];
        for (int i = 0; i < sz; i++) {
            booleans[i] = bool();
        }
        return Arrays.asList(booleans);
    }

    private static int read4(byte[] b, int off) {
        return (b[off] & 0xFF)
            | (b[off + 1] & 0xFF) << 8
            | (b[off + 2] & 0xFF) << 16
            | (b[off + 3] & 0xFF) << 24;
    }

    static long read8(byte[] b, int off) {
        return (long) (b[off] & 0xFF)
            | (long) (b[off + 1] & 0xFF) << 8
            | (long) (b[off + 2] & 0xFF) << 16
            | (long) (b[off + 3] & 0xFF) << 24
            | (long) (b[off + 4] & 0xFF) << 32
            | (long) (b[off + 5] & 0xFF) << 40
            | (long) (b[off + 6] & 0xFF) << 48
            | (long) (b[off + 7] & 0xFF) << 56;
    }

    public static void checkFields(long fieldSet, long expected, String type) {
        if ((fieldSet & expected) != expected) {
            throw new RuntimeException("missing required " + type + " fields");
        }
    }

    public static void checkUnset(long fieldSet, String type, int index) {
        if (fieldSet != 0) {
            throw new RuntimeException(type + " " + index + " is already set");
        }
    }

    public static void checkFieldsExact(long fieldSet, long expected, String type) {
        if (fieldSet != expected) {
            throw new RuntimeException(
                "incorrect fieldset for " + type + ": expected " + expected + ", got " + fieldSet
            );
        }
    }
}
