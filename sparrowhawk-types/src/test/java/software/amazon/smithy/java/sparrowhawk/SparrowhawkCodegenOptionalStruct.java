/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static java.nio.charset.StandardCharsets.UTF_8;
import static software.amazon.smithy.java.sparrowhawk.KConstants.*;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.*;

import java.util.Objects;


public final class SparrowhawkCodegenOptionalStruct implements SparrowhawkObject {
    private static final long REQUIRED_LIST_0 = 0x8L;
    private long $list_0 = REQUIRED_LIST_0;
    // list fieldSet 0 index 1
    private static final long FIELD_STRING = 0x8L;
    private Object string;

    public String getString() {
        if (string == null) {
            return null;
        }
        if (string instanceof String) {
            return (String) string;
        }
        String s = new String((byte[]) string, UTF_8);
        this.string = s;
        return s;
    }

    public void setString(String string) {
        if (string == null) {
            missingField("'string' is required");
        }
        this.string = string;
        this.$size = 0;
    }

    public boolean hasString() {
        return ($list_0 & FIELD_STRING) != 0;
    }

    private static final long REQUIRED_EIGHT_BYTE_0 = 0xbL;
    private long $eightByte_0 = REQUIRED_EIGHT_BYTE_0;
    // eightByte fieldSet 0 index 1
    private static final long FIELD_TIMESTAMP = 0x8L;
    private double timestamp;

    public double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(double timestamp) {
        this.timestamp = timestamp;
        this.$size = 0;
    }

    public boolean hasTimestamp() {
        return ($eightByte_0 & FIELD_TIMESTAMP) != 0;
    }

    private int $size;

    public int size() {
        if ($size > 0) {
            return $size;
        }

        int size = ($list_0 == 0x0L ? 0 : (ulongSize($list_0))) + ($eightByte_0 == 0x3L
            ? 0
            : (ulongSize($eightByte_0)));
        size += sizeListFields();
        size += sizeEightByteFields();
        this.$size = size;
        return size;
    }

    private int sizeEightByteFields() {
        int size = 8;
        return size;
    }

    private int sizeListFields() {
        int size = 0;
        size += $stringLen();
        return size;
    }

    private int $stringLen() {
        Object field = string;
        if (field == null) {
            missingField("Required field 'string' is missing");
        }

        int size;
        if (field.getClass() == byte[].class) {
            size = ((byte[]) field).length;
        } else {
            byte[] bytes = ((String) field).getBytes(UTF_8);
            this.string = bytes;
            size = bytes.length;
        }

        return byteListLengthEncodedSize(size);
    }

    public void encodeTo(SparrowhawkSerializer s) {
        s.writeVarUL(encodeByteListLength(size()));
        writeEightByteFields(s);
        writeListFields(s);
    }

    private void writeEightByteFields(SparrowhawkSerializer s) {
        if ($eightByte_0 != 0x3L) {
            s.writeVarUL($eightByte_0);
            s.writeDouble(timestamp);
        }
    }

    private void writeListFields(SparrowhawkSerializer s) {
        if ($list_0 != 0x0L) {
            s.writeVarUL($list_0);
            s.writeBytes(string);
        }
    }

    public void decodeFrom(SparrowhawkDeserializer d) {
        int size = (int) decodeElementCount(d.varUI());
        this.$size = size;
        int start = d.pos();

        while ((d.pos() - start) < size) {
            long fieldSet = d.varUL();
            int fieldSetIdx = ((fieldSet & 0b100) != 0) ? d.varUI() + 1 : 0;
            int type = (int) (fieldSet & 3);
            if (type == T_LIST) {
                if (fieldSetIdx != 0) { throw new IllegalArgumentException("unknown fieldSetIdx " + fieldSetIdx); }
                decodeListFieldSet0(d, fieldSet);
            } else if (type == T_EIGHT) {
                if (fieldSetIdx != 0) { throw new IllegalArgumentException("unknown fieldSetIdx " + fieldSetIdx); }
                decodeEightByteFieldSet0(d, fieldSet);
            } else {
                throw new RuntimeException("Unexpected field set type: " + type);
            }
        }
    }

    private void decodeEightByteFieldSet0(SparrowhawkDeserializer d, long fieldSet) {
        SparrowhawkDeserializer.checkFields(fieldSet, REQUIRED_EIGHT_BYTE_0, "eight-byte");
        this.$eightByte_0 = fieldSet;
        this.timestamp = d.d8();
    }

    private void decodeListFieldSet0(SparrowhawkDeserializer d, long fieldSet) {
        SparrowhawkDeserializer.checkFields(fieldSet, REQUIRED_LIST_0, "lists");
        this.$list_0 = fieldSet;
        {
            this.string = d.string();
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SparrowhawkCodegenOptionalStruct)) return false;
        SparrowhawkCodegenOptionalStruct o = (SparrowhawkCodegenOptionalStruct) other;
        if (timestamp != o.timestamp) {
            return false;
        }
        if (!Objects.equals(getString(), o.getString())) {
            return false;
        }
        return true;
    }
}
