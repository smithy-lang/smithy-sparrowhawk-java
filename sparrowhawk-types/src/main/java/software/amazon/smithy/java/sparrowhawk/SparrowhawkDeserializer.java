/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.T_EIGHT;
import static software.amazon.smithy.java.sparrowhawk.KConstants.T_FOUR;
import static software.amazon.smithy.java.sparrowhawk.KConstants.T_LIST;
import static software.amazon.smithy.java.sparrowhawk.KConstants.T_VARINT;
import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeByteListLength;
import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeElementCount;
import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeLenPrefixedListLengthChecked;
import static software.amazon.smithy.java.sparrowhawk.KConstants.listType;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.EXACTLY_ONE_LIST;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

public final class SparrowhawkDeserializer {
    private static final long ALL_FIELDS_UNKNOWN = -1 << 3;
    // this should inline but if not, replace with method handles
    private static final JdkCompat JDK_COMPAT;

    private interface JdkCompat {
        BigInteger makeBigInteger(byte[] b, int off, int len);
    }

    private static final class Jdk8 implements JdkCompat {
        @Override
        public BigInteger makeBigInteger(byte[] b, int off, int len) {
            byte[] copy = new byte[len];
            System.arraycopy(b, off, copy, 0, len);
            return new BigInteger(copy);
        }
    }

    private static final class Jdk9 implements JdkCompat {
        @Override
        public BigInteger makeBigInteger(byte[] b, int off, int len) {
            return new BigInteger(b, off, len);
        }
    }

    static {
        JdkCompat compat;
        try {
            Runtime.class.getMethod("version");
            compat = new Jdk9();
        } catch (NoSuchMethodException e) {
            compat = new Jdk8();
        }
        JDK_COMPAT = compat;
    }

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
            throw new RuntimeException("still has " + (len - pos) + " bytes");
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

    public BigInteger bigInteger() {
        int len = (int) varUL();
        if (!KConstants.isByteListLength(len)) {
            throw new RuntimeException("not bytes: " + listType(len));
        }
        int decodedLen = decodeByteListLength(len);
        BigInteger bi = JDK_COMPAT.makeBigInteger(b, pos, decodedLen);
        pos += decodedLen;
        return bi;
    }

    public BigDecimal bigDecimal() {
        BigDecimalHolder holder = new BigDecimalHolder();
        holder.decodeFrom(this);
        return holder.toBigDecimal();
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

    public ByteBuffer bytesCopied() {
        int len = (int) varUL();
        if (!KConstants.isByteListLength(len)) {
            throw new RuntimeException("not bytes: " + listType(len));
        }
        int decodedLen = decodeByteListLength(len);
        byte[] copy = new byte[decodedLen];
        System.arraycopy(b, pos, copy, 0, decodedLen);
        pos += decodedLen;
        return ByteBuffer.wrap(copy);
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

        int len = 1 + varintLength(f);
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
        return new ByteList(this);
    }

    public List<Short> decodeShortList() {
        return new ShortList(this);
    }

    public List<Integer> decodeIntegerList() {
        return new IntegerList(this);
    }

    public List<Long> decodeLongList() {
        return new LongList(this);
    }

    public List<Double> decodeDoubleList() {
        return new DoubleList(this);
    }

    public List<Date> decodeDateList() {
        return new DateList(this);
    }

    public List<Float> decodeFloatList() {
        return new FloatList(this);
    }

    public List<Boolean> decodeBooleanList() {
        return new BooleanList(this);
    }

    public List<ByteBuffer> decodeByteBufferList() {
        return new ByteBufferList(this);
    }

    public List<ByteBuffer> decodeCopiedByteBufferList() {
        return new CopiedByteBufferList(this);
    }

    public List<ByteBuffer> decodeSparseBlobList() {
        ArrayList<ByteBuffer> l = new ArrayList<>();
        int count = decodeLenPrefixedListLengthChecked(varUL());
        for (int i = 0; i < count; i++) {
            if (varUL() == 0) { // len
                l.add(null);
            } else if (b[pos++] != EXACTLY_ONE_LIST) { // fieldset
                badSparseList();
            } else {
                l.add(bytes());
            }
        }
        return l;
    }

    public List<ByteBuffer> decodeCopiedSparseBlobList() {
        ArrayList<ByteBuffer> l = new ArrayList<>();
        int count = decodeLenPrefixedListLengthChecked(varUL());
        for (int i = 0; i < count; i++) {
            if (varUL() == 0) { // len
                l.add(null);
            } else if (b[pos++] != EXACTLY_ONE_LIST) { // fieldset
                badSparseList();
            } else {
                l.add(bytesCopied());
            }
        }
        return l;
    }

    public <T extends SparrowhawkObject> List<T> decodeSparseObjectList(Supplier<T> factory) {
        ArrayList<T> l = new ArrayList<>();
        int count = decodeLenPrefixedListLengthChecked(varUL());
        for (int i = 0; i < count; i++) {
            if (varUL() == 0) { // len
                l.add(null);
            } else if (b[pos++] != EXACTLY_ONE_LIST) { // fieldset
                badSparseList();
            } else {
                T obj = factory.get();
                obj.decodeFrom(this);
                l.add(obj);
            }
        }
        return l;
    }

    private static void badSparseList() {
        throw new ParseException("improperly encoded sparse list");
    }

    public void skipRemaining(long fieldset, int type) {
        long bitmask = ALL_FIELDS_UNKNOWN;
        switch (type) {
            case T_LIST:
                skipRemainingLists(fieldset, bitmask);
                break;
            case T_VARINT:
                skipRemainingVarints(fieldset, bitmask);
                break;
            case T_FOUR:
                skipRemainingFours(fieldset, bitmask);
                break;
            case T_EIGHT:
                skipRemainingEights(fieldset, bitmask);
                break;
        }
    }

    public void skipAllVarints(long fieldset) {
        skipRemainingVarints(fieldset, ALL_FIELDS_UNKNOWN);
    }

    public void skipRemainingVarints(long fieldset, long unknownFieldBitmask) {
        int unknowns = Long.bitCount(fieldset & unknownFieldBitmask);
        if (unknowns != 0) {
            doSkipVarints(unknowns);
        }
    }

    private void doSkipVarints(int toSkip) {
        for (int i = 0; i < toSkip; i++) {
            int f = b[pos++] & 0xFF;
            pos += varintLength(f);
        }
    }

    public void skipAllEights(long fieldset) {
        skipRemainingEights(fieldset, ALL_FIELDS_UNKNOWN);
    }

    public void skipRemainingEights(long fieldset, long unknownFieldBitmask) {
        int unknowns = Long.bitCount(fieldset & unknownFieldBitmask);
        pos += (8 * unknowns);
    }

    public void skipAllFours(long fieldset) {
        skipRemainingFours(fieldset, ALL_FIELDS_UNKNOWN);
    }

    public void skipRemainingFours(long fieldset, long unknownFieldBitmask) {
        int unknowns = Long.bitCount(fieldset & unknownFieldBitmask);
        pos += (4 * unknowns);
    }

    public void skipAllLists(long fieldset) {
        skipRemainingLists(fieldset, ALL_FIELDS_UNKNOWN);
    }

    public void skipRemainingLists(long fieldset, long unknownFieldBitmask) {
        int unknowns = Long.bitCount(fieldset & unknownFieldBitmask);
        if (unknowns != 0) {
            skipLengthPrefixedList(unknowns);
        }
    }

    private void skipLengthPrefixedList(int toSkip) {
        for (int i = 0; i < toSkip; i++) {
            doSkipList(varUI());
        }
    }

    private void doSkipList(int len) {
        int count = decodeElementCount(len);
        switch (len & 7) {
            case KConstants.LIST_FOUR:
                pos += 4 * count;
                break;
            case KConstants.LIST_EIGHT:
                pos += 8 * count;
                break;
            case KConstants.LIST_VARINTS:
                skipVarintList(count);
                break;
            case KConstants.LIST_LEN_DELIMITED_ITEMS:
                skipLengthPrefixedList(count);
                break;
            default:
                if ((len & 1) != 0) {
                    unknownListType();
                }
                pos += count;
                break;
        }
    }

    private static void unknownListType() {
        throw new ParseException("unknown list type");
    }

    private void skipVarintList(int count) {
        for (int i = 0; i < count; i++) {
            varUL();
        }
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

    private static int varintLength(int i) {
        return Integer.numberOfTrailingZeros(i | (1 << 8));
    }
}
