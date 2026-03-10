/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeByteListLength;
import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeEightBListLength;
import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeFourBListLength;
import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeLenPrefixedListLength;
import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeVarintListLength;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public final class SparrowhawkSerializer {
    public static final byte EMPTY_LIST_SIZE_VARINT = 1;
    static final byte BOOL_FALSE = 1, BOOL_TRUE = 3;
    static final byte EXACTLY_ONE_LIST = encodeByteLE64(KConstants.listField(1));

    private int position;
    private final byte[] payload;

    public SparrowhawkSerializer(int len) {
        this.payload = new byte[byteListLengthEncodedSize(len)];
    }

    public SparrowhawkSerializer(byte[] payload) {
        this.payload = payload;
    }

    public int position() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public void writeRawByte(byte b) {
        payload[position++] = b;
    }

    public void writeEmptyObject() {
        payload[position++] = 1;
    }

    public void checkFull() {
        if (position != payload.length) {
            notFull();
        }
    }

    public byte[] payload() {
        checkFull();
        return payload;
    }

    private void notFull() {
        throw new IllegalStateException("wrote " + position + " bytes, expected " + payload.length);
    }

    public void writeVarL(long i) {
        doWriteVar8(zigzag8(i));
    }

    public void writeVar1(int i) {
        payload[position++] = (byte) (2 * i + 1);
    }

    public void writeVarUL(int i) {
        doWriteVar4(i);
    }

    public void writeVarUL(long i) {
        doWriteVar8(i);
    }

    public void writeVarUI(int i) {
        doWriteVar4(i);
    }

    public void writeVarB(byte b) {
        writeVarI(b);
    }

    public void writeVarS(short s) {
        writeVarI(s);
    }

    public void writeVarI(int i) {
        doWriteVar4(zigzag4(i));
    }

    public void writeBool(boolean b) {
        payload[position++] = b ? BOOL_TRUE : BOOL_FALSE;
    }

    private void doWriteVar4(int i) {
        // TODO: don't be lazy
        doWriteVar8(Integer.toUnsignedLong(i));
    }

    private void doWriteVar8(long i) {
        int bits = 64 - Long.numberOfLeadingZeros(i | 1);
        if (bits < 8) {
            payload[position++] = (byte) (2 * i + 1);
        } else if (bits > 56) {
            payload[position++] = 0;
            write8(i);
        } else {
            writeVar8Slow0(bits, i);
        }
    }

    private void writeVar8Slow0(int bits, long i) {
        int bytes = 1 + (bits - 1) / 7;
        i = (2 * i + 1) << (bytes - 1);
        for (int l = 0; l < bytes; l++) {
            payload[position++] = (byte) (i & 0xFF);
            i >>>= 8;
        }
    }

    public void write4(int i) {
        payload[position++] = (byte) (i);
        payload[position++] = (byte) ((i >>> 8) & 0xFF);
        payload[position++] = (byte) ((i >>> 16) & 0xFF);
        payload[position++] = (byte) ((i >>> 24) & 0xFF);
    }

    public void write8(long i) {
        payload[position++] = (byte) (i);
        payload[position++] = (byte) ((i >>> 8) & 0xFF);
        payload[position++] = (byte) ((i >>> 16) & 0xFF);
        payload[position++] = (byte) ((i >>> 24) & 0xFF);
        payload[position++] = (byte) ((i >>> 32) & 0xFF);
        payload[position++] = (byte) ((i >>> 40) & 0xFF);
        payload[position++] = (byte) ((i >>> 48) & 0xFF);
        payload[position++] = (byte) ((i >>> 56) & 0xFF);
    }

    public void writeFloat(float f) {
        write4(Float.floatToIntBits(f));
    }

    public void writeFloat(Float f) {
        writeFloat(f.floatValue());
    }

    public void writeDouble(double d) {
        write8(Double.doubleToLongBits(d));
    }

    public void writeDate(Date d) {
        writeDouble(d.getTime() / 1000d);
    }

    public void writeInstant(Instant i) {
        writeDouble(i.toEpochMilli() / 1000d);
    }

    public void writeBigDecimal(Object o) {
        BigDecimalHolder holder = (BigDecimalHolder) o;
        holder.encodeTo(this);
    }

    public void writeBytes(ByteBuffer b) {
        int len = b.remaining();
        if (b.hasArray()) {
            writeBytes(b.array(), b.arrayOffset() + b.position(), len);
        } else {
            doWriteVar8(encodeByteListLength(len));
            int pos = b.position();
            b.get(payload, position, len);
            b.position(pos);
            position += len;
        }
    }

    public void writeString(String s) {
        writeBytes(s.getBytes(StandardCharsets.UTF_8));
    }

    public void writeBytes(byte[] b) {
        writeBytes(b, 0, b.length);
    }

    // todo: writeString with null-check message
    public void writeBytes(Object o) {
        writeBytes((byte[]) o);
    }

    public void writeBytes(byte[] b, int off, int len) {
        doWriteVar8(encodeByteListLength(len));
        System.arraycopy(b, off, payload, position, len);
        position += len;
    }

    public void writeEncodedObject(ByteBuffer b) {
        int len = b.remaining();
        if (b.hasArray()) {
            writeEncodedObject(b.array(), b.arrayOffset() + b.position(), len);
        } else {
            int pos = b.position();
            b.get(payload, position, len);
            b.position(pos);
            position += len;
        }
    }

    public void writeEncodedObject(byte[] b, int off, int len) {
        System.arraycopy(b, off, payload, position, len);
        position += len;
    }

    private static long zigzag8(long i) {
        return (i << 1) ^ (i >> 63);
    }

    private static int zigzag4(int i) {
        return (i << 1) ^ (i >> 31);
    }

    public void writeIntegerList(List<Integer> list) {
        int sz = list.size();
        writeVarUL(encodeVarintListLength(sz));
        for (int i = 0; i < sz; i++) {
            writeVarI(list.get(i));
        }
    }

    public void writeIntegerList(Integer[] list) {
        int sz = list.length;
        writeVarUL(encodeVarintListLength(sz));
        for (int i = 0; i < sz; i++) {
            writeVarI(list[i]);
        }
    }

    public void writeByteList(Byte[] list) {
        int sz = list.length;
        writeVarUL(encodeVarintListLength(sz));
        for (int i = 0; i < sz; i++) {
            writeVarI(list[i]);
        }
    }

    public void writeByteList(List<Byte> list) {
        int sz = list.size();
        writeVarUL(encodeVarintListLength(sz));
        for (int i = 0; i < sz; i++) {
            writeVarI(list.get(i));
        }
    }

    public void writeShortList(Short[] list) {
        int sz = list.length;
        writeVarUL(encodeVarintListLength(sz));
        for (int i = 0; i < sz; i++) {
            writeVarI(list[i]);
        }
    }

    public void writeShortList(List<Short> list) {
        int sz = list.size();
        writeVarUL(encodeVarintListLength(sz));
        for (int i = 0; i < sz; i++) {
            writeVarI(list.get(i));
        }
    }

    public void writeLongList(List<Long> list) {
        int sz = list.size();
        writeVarUL(encodeVarintListLength(sz));
        for (int i = 0; i < sz; i++) {
            writeVarL(list.get(i));
        }
    }

    public void writeLongList(Long[] list) {
        int sz = list.length;
        writeVarUL(encodeVarintListLength(sz));
        for (int i = 0; i < sz; i++) {
            writeVarL(list[i]);
        }
    }

    public void writeBooleanList(List<Boolean> list) {
        int sz = list.size();
        writeVarUL(encodeVarintListLength(sz));
        for (int i = 0; i < sz; i++) {
            writeBool(list.get(i));
        }
    }

    public void writeBooleanList(Boolean[] list) {
        int sz = list.length;
        writeVarUL(encodeVarintListLength(sz));
        for (int i = 0; i < sz; i++) {
            writeBool(list[i]);
        }
    }

    public void writeFloatList(List<Float> list) {
        int sz = list.size();
        writeVarUL(encodeFourBListLength(sz));
        for (int i = 0; i < sz; i++) {
            writeFloat(list.get(i));
        }
    }

    public void writeFloatList(Float[] list) {
        int sz = list.length;
        writeVarUL(encodeFourBListLength(sz));
        for (int i = 0; i < sz; i++) {
            writeFloat(list[i]);
        }
    }

    public void writeDoubleList(Double[] list) {
        int sz = list.length;
        writeVarUL(encodeEightBListLength(sz));
        for (int i = 0; i < sz; i++) {
            writeDouble(list[i]);
        }
    }

    public void writeDoubleList(List<Double> list) {
        int sz = list.size();
        writeVarUL(encodeEightBListLength(sz));
        for (int i = 0; i < sz; i++) {
            writeDouble(list.get(i));
        }
    }

    public void writeDateList(List<Date> list) {
        int sz = list.size();
        writeVarUL(encodeEightBListLength(sz));
        for (int i = 0; i < sz; i++) {
            writeDate(list.get(i));
        }
    }

    public void writeDateList(Date[] list) {
        int sz = list.length;
        writeVarUL(encodeEightBListLength(sz));
        for (int i = 0; i < sz; i++) {
            writeDate(list[i]);
        }
    }

    public void writeBlobList(List<ByteBuffer> list) {
        int sz = list.size();
        writeVarUL(encodeLenPrefixedListLength(sz));
        for (int i = 0; i < sz; i++) {
            writeBytes(list.get(i));
        }
    }

    public void writeSparseBlobList(List<ByteBuffer> blobs) {
        writeVarUL(encodeLenPrefixedListLength(blobs.size()));
        for (ByteBuffer blob : blobs) {
            if (blob == null) {
                writeEmptyObject();
            } else {
                int s = blob.remaining() + ulongSize(encodeByteListLength(blob.remaining())) + 1;
                writeVarUL(encodeByteListLength(s));
                writeRawByte(EXACTLY_ONE_LIST);
                writeBytes(blob);
            }
        }
    }

    public static int sparseBlobListSize(List<ByteBuffer> blobs) {
        int size = ulongSize(encodeLenPrefixedListLength(blobs.size()));
        for (ByteBuffer blob : blobs) {
            if (blob != null) {
                // size = blob size + blob length tag + 1 fieldset + length tag for the preceding
                int s = blob.remaining() + ulongSize(encodeByteListLength(blob.remaining())) + 1;
                s += ulongSize(encodeByteListLength(s));
                size += s;
            } else {
                size++;
            }
        }
        return size;
    }

    public void writeSparseObjectList(List<? extends SparrowhawkObject> objects) {
        writeVarUL(encodeLenPrefixedListLength(objects.size()));
        for (SparrowhawkObject object : objects) {
            if (object == null) {
                writeEmptyObject();
            } else {
                int s = object.size() + ulongSize(encodeByteListLength(object.size())) + 1;
                writeVarUL(encodeByteListLength(s));
                writeRawByte(EXACTLY_ONE_LIST);
                object.encodeTo(this);
            }
        }
    }

    public static int sparseObjectListSize(List<? extends SparrowhawkObject> objects) {
        int size = ulongSize(encodeLenPrefixedListLength(objects.size()));
        for (SparrowhawkObject object : objects) {
            if (object != null) {
                // size = object size + object length tag + 1 fieldset + length tag for the preceding
                int s = object.size() + ulongSize(encodeByteListLength(object.size())) + 1;
                s += ulongSize(encodeByteListLength(s));
                size += s;
            } else {
                size++;
            }
        }
        return size;
    }

    private static final int I_1B = (~0 << 7);
    private static final int I_2B = (~0 << 14);
    private static final int I_3B = (~0 << 21);
    private static final int I_4B = (~0 << 28);
    private static final long L_5B = (~0L << 35);
    private static final long L_6B = (~0L << 42);
    private static final long L_7B = (~0L << 49);
    private static final long L_8B = (~0L << 56);

    public static int intSize(int i) {
        return uintSize(zigzag4(i));
    }

    public static int intSize(boolean b) {
        return 1;
    }

    public static int uintSize(int i) {
        if ((i & I_1B) == 0) {
            return 1;
        } else if ((i & I_2B) == 0) {
            return 2;
        } else if ((i & I_3B) == 0) {
            return 3;
        } else if ((i & I_4B) == 0) {
            return 4;
        } else {
            return 5;
        }
    }

    public static int longSize(long i) {
        return ulongSize(zigzag8(i));
    }

    public static int longSize(boolean b) {
        return 1;
    }

    public static int ulongSize(long i) {
        if ((i & I_1B) == 0) {
            return 1;
        } else if ((i & I_2B) == 0) {
            return 2;
        } else if ((i & I_3B) == 0) {
            return 3;
        } else if ((i & I_4B) == 0) {
            return 4;
        } else if ((i & L_5B) == 0) {
            return 5;
        } else if ((i & L_6B) == 0) {
            return 6;
        } else if ((i & L_7B) == 0) {
            return 7;
        } else if ((i & L_8B) == 0) {
            return 8;
        } else {
            return 9;
        }
    }

    public static int byteListLengthEncodedSize(int len) {
        return len + ulongSize(encodeByteListLength(len));
    }

    public static int lenPrefixedListLengthEncodedSize(int size, int elements) {
        return size + ulongSize(encodeLenPrefixedListLength(elements));
    }

    public static int byteListLengthEncodedSize(ByteBuffer buf) {
        return byteListLengthEncodedSize(buf.remaining());
    }

    public static void missingField(String message) {
        throw new NullPointerException(message);
    }

    public static int blobListEncodedSize(List<ByteBuffer> list) {
        int len = list.size();
        int sz = uintSize(encodeLenPrefixedListLength(len));
        for (int i = 0; i < len; i++) {
            sz += byteListLengthEncodedSize(list.get(i));
        }
        return sz;
    }

    public static byte encodeByteLE64(long b) {
        long enc = zigzag8(b);
        if (ulongSize(enc) > 1) {
            throw new RuntimeException("can't encode " + b + " into one byte");
        }
        return (byte) enc;
    }
}
