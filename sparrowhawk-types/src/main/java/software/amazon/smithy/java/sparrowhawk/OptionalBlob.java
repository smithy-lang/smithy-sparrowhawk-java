/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.*;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.byteListLengthEncodedSize;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.ulongSize;

import java.nio.ByteBuffer;
import java.util.Objects;


public final class OptionalBlob implements SparrowhawkObject {
    private static final long REQUIRED_LIST_0 = 0x0L;
    private long $list_0 = REQUIRED_LIST_0;
    // list fieldSet 0 index 1
    private static final long FIELD_ITEM = 0x8L;
    private ByteBuffer item;

    public ByteBuffer getItem() {
        return item;
    }

    public void setItem(ByteBuffer item) {
        if (item == null) {
            $list_0 &= ~FIELD_ITEM;
        } else {
            $list_0 |= FIELD_ITEM;
        }
        this.item = item;
        this.$size = -1;
    }

    public boolean hasItem() {
        return ($list_0 & FIELD_ITEM) != 0;
    }

    private int $size;

    public int size() {
        if ($size >= 0) {
            return $size;
        }

        int size = ($list_0 == 0x0L ? 0 : (ulongSize($list_0)));
        size += sizeListFields();
        this.$size = size;
        return size;
    }

    private int sizeListFields() {
        int size = 0;
        if (hasItem()) {
            size += byteListLengthEncodedSize(item.remaining());
        }
        return size;
    }

    public void encodeTo(SparrowhawkSerializer s) {
        s.writeVarUL(encodeByteListLength(size()));
        writeListFields(s);
    }

    private void writeListFields(SparrowhawkSerializer s) {
        if ($list_0 != 0x0L) {
            s.writeVarUL($list_0);
            if (hasItem()) {
                s.writeBytes(item);
            }
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
            } else {
                throw new RuntimeException("Unexpected field set type: " + type);
            }
        }
    }

    private void decodeListFieldSet0(SparrowhawkDeserializer d, long fieldSet) {
        this.$list_0 = fieldSet;
        if (hasItem()) {
            this.item = d.bytes();
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OptionalBlob)) return false;
        OptionalBlob o = (OptionalBlob) other;
        return Objects.equals(getItem(), o.getItem());
    }

    @Override
    public int hashCode() {
        return Objects.hash($list_0, item, $size);
    }
}
