package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.T_LIST;
import static software.amazon.smithy.java.sparrowhawk.KConstants.T_VARINT;
import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeElementCount;
import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeByteListLength;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.byteListLengthEncodedSize;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.intSize;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.missingField;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.ulongSize;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;


public final class BigDecimalHolder implements SparrowhawkObject {
    private static final long REQUIRED_VARINT_0 = 0x9L;
    private static final long UNKNOWN_MASK_VARINT_0 = 0xfffffffffffffff0L;
    private long $varint_0 = REQUIRED_VARINT_0;
    // varint fieldSet 0 index 1
    private static final long FIELD_EXPONENT = 0x8L;
    private int exponent;

    public int getExponent() {
        return exponent;
    }

    public void setExponent(int exponent) {
        this.exponent = exponent;
        this.$size = -1;
    }

    public boolean hasExponent() {
        return ($varint_0 & FIELD_EXPONENT) != 0;
    }

    private static final long REQUIRED_LIST_0 = 0x8L;
    private static final long UNKNOWN_MASK_LIST_0 = 0xfffffffffffffff0L;
    private long $list_0 = REQUIRED_LIST_0;
    // list fieldSet 0 index 1
    private static final long FIELD_MANTISSA = 0x8L;
    private Object mantissa;

    public BigDecimalHolder(BigDecimal bd) {
        this.mantissa = bd.unscaledValue();
        this.exponent = -bd.scale();
    }

    public BigDecimalHolder() {

    }

    public BigDecimal toBigDecimal() {
        return new BigDecimal(getMantissa(), -getExponent());
    }

    public BigInteger getMantissa() {
        if (mantissa == null) {
            return null;
        }
        if (mantissa instanceof BigInteger) {
            return (BigInteger) mantissa;
        }
        BigInteger bi = new BigInteger((byte[]) mantissa);
        this.mantissa = bi;
        return bi;
    }

    public void setMantissa(BigInteger mantissa) {
        if (mantissa == null) {
            missingField("'mantissa' is required");
        }
        this.mantissa = mantissa;
        this.$size = -1;
    }

    public boolean hasMantissa() {
        return ($list_0 & FIELD_MANTISSA) != 0;
    }

    private int $size = -1;

    public int size() {
        if ($size >= 0) {
            return $size;
        }

        int size = ($varint_0 == 0x1L ? 0 : (ulongSize($varint_0))) + ($list_0 == 0x0L ? 0 : (ulongSize($list_0)));
        size += sizeVarints();
        size += sizeListFields();
        this.$size = size;
        return size;
    }

    private int sizeVarints() {
        int size = 0;
        size += intSize(exponent);
        return size;
    }

    private int sizeListFields() {
        int size = 0;
        size += $mantissaSize();
        return size;
    }

    private int $mantissaSize() {
        if (mantissa == null) {
            missingField("Required field 'mantissa' is missing");
        }
        int size;
        if (mantissa.getClass() == byte[].class) {
            size = ((byte[]) mantissa).length;
        } else {
            byte[] bytes = ((BigInteger) mantissa).toByteArray();
            this.mantissa = bytes;
            size = bytes.length;
        }

        return byteListLengthEncodedSize(size);
    }

    public void encodeTo(SparrowhawkSerializer s) {
        s.writeVarUL(encodeByteListLength(size()));
        writeVarints(s);
        writeListFields(s);
    }

    private void writeVarints(SparrowhawkSerializer s) {
        if ($varint_0 != 0x1L) {
            s.writeVarUL($varint_0);
            s.writeVarI(exponent);
        }
    }

    private void writeListFields(SparrowhawkSerializer s) {
        if ($list_0 != 0x0L) {
            s.writeVarUL($list_0);
            s.writeBytes(mantissa);
        }
    }

    public void decodeFrom(SparrowhawkDeserializer d) {
        int size = (int) decodeElementCount(d.varUI());
        this.$size = size;
        int start = d.pos();
        int end = start + size;

        while (d.pos() < end) {
            long fieldSet = d.varUL();
            int fieldSetIdx = ((fieldSet & 0b100) != 0) ? d.varUI() + 1 : 0;
            int type = (int) (fieldSet & 3);
            if (type == T_LIST) {
                decodeListFieldSet(d, fieldSetIdx, fieldSet);
            } else if (type == T_VARINT) {
                decodeVarintFieldSet(d, fieldSetIdx, fieldSet);
            } else {
                d.skipRemaining(fieldSet, type);
            }
        }
    }

    private void decodeVarintFieldSet(SparrowhawkDeserializer d, int fieldSetIdx, long fieldSet) {
        switch (fieldSetIdx) {
            case 0:
                decodeVarintFieldSet0(d, fieldSet);
                break;
            default:
                d.skipAllVarints(fieldSet);
                break;
        }
    }

    private void decodeVarintFieldSet0(SparrowhawkDeserializer d, long fieldSet) {
        SparrowhawkDeserializer.checkFields(fieldSet, REQUIRED_VARINT_0, "varint");
        this.$varint_0 = fieldSet;
        this.exponent = d.varI();
        d.skipRemainingVarints(fieldSet, UNKNOWN_MASK_VARINT_0);
    }

    private void decodeListFieldSet(SparrowhawkDeserializer d, int fieldSetIdx, long fieldSet) {
        switch (fieldSetIdx) {
            case 0:
                decodeListFieldSet0(d, fieldSet);
                break;
            default:
                d.skipAllLists(fieldSet);
                break;
        }
    }

    private void decodeListFieldSet0(SparrowhawkDeserializer d, long fieldSet) {
        SparrowhawkDeserializer.checkFields(fieldSet, REQUIRED_LIST_0, "lists");
        this.$list_0 = fieldSet;
        {
            this.mantissa = d.bigInteger();
        }
        d.skipRemainingLists(fieldSet, UNKNOWN_MASK_LIST_0);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BigDecimalHolder)) return false;
        BigDecimalHolder o = (BigDecimalHolder) other;
        if (exponent != o.exponent) {
            return false;
        }
        if (!Objects.equals(getMantissa(), o.getMantissa())) {
            return false;
        }
        return true;
    }
}
