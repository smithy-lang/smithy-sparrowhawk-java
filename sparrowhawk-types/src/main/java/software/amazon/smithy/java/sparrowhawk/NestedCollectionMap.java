package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeElementCount;
import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeByteListLength;
import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeLenPrefixedListLength;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.EMPTY_LIST_SIZE_VARINT;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.byteListLengthEncodedSize;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.ulongSize;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public abstract class NestedCollectionMap<U> extends SparrowhawkMap<U> {
    private static final long REQUIRED_LIST_FIELDSET_0 = KConstants.listField(0b11);
    private static final ByteBuffer[] EMPTY_KEYS = new ByteBuffer[0];
    private static final Object[] EMPTY_VALUES = new Object[0];

    protected ByteBuffer[] keys = EMPTY_KEYS;
    protected Object[] values = EMPTY_VALUES;
    protected transient int $size = -1;

    protected abstract Object[] readValues(SparrowhawkDeserializer d, int n);

    protected abstract void writeValues(SparrowhawkSerializer s);

    protected abstract int sizeofValues();

    protected abstract int decodeValueCount(int encodedCount);

    protected final void setEmpty() {
        keys = EMPTY_KEYS;
        values = EMPTY_VALUES;
        $size = 0;
    }

    protected final void init(ByteBuffer[] keys, Object[] values, int size) {
        checkConsistent(keys, values);
        this.keys = keys;
        this.values = values;
        this.$size = size;
    }

    private static void checkConsistent(ByteBuffer[] keys, Object[] values) {
        if (keys.length != values.length) {
            throw new IllegalStateException(
                "key/value length mismatch: " + keys.length + " keys, " + values.length + " values"
            );
        }
    }

    protected static String string(ByteBuffer b) {
        return new String(b.array(), b.arrayOffset() + b.position(), b.remaining(), StandardCharsets.UTF_8);
    }

    @Override
    public final void decodeFrom(SparrowhawkDeserializer d) {
        int size = (int) decodeElementCount(d.varUI());
        $size = size;
        if (size > 0) {
            long fieldset = d.varUL();
            if ((fieldset & 3) != KConstants.T_LIST) {
                throw new RuntimeException("bad field type: " + KConstants.fieldType(fieldset));
            }
            if ((fieldset & REQUIRED_LIST_FIELDSET_0) != REQUIRED_LIST_FIELDSET_0) {
                throw new RuntimeException("missing required fields");
            }
            int nkeys = (int) decodeElementCount(d.varUI());
            d.checkElementCount(nkeys);
            keys = readKeys(d, nkeys);
            int nvalues = decodeValueCount(d.varUI());
            if (nkeys != nvalues) {
                throw new RuntimeException("mismatch in key and value lengths");
            }
            values = readValues(d, nvalues);
        } else {
            keys = EMPTY_KEYS;
            values = EMPTY_VALUES;
        }
    }

    private static ByteBuffer[] readKeys(SparrowhawkDeserializer d, int n) {
        ByteBuffer[] bs = new ByteBuffer[n];
        for (int i = 0; i < bs.length; i++) {
            bs[i] = d.bytes();
        }
        return bs;
    }

    @Override
    public final void encodeTo(SparrowhawkSerializer s) {
        int size = size();
        if (size > 0) {
            s.writeVarUL(encodeByteListLength(size));
            s.writeVarUL(REQUIRED_LIST_FIELDSET_0);
            s.writeVarUL(encodeLenPrefixedListLength(keys.length));
            for (int i = 0; i < keys.length; i++) {
                s.writeBytes(keys[i]);
            }
            writeValues(s);
        } else {
            s.writeRawByte(EMPTY_LIST_SIZE_VARINT);
        }
    }

    @Override
    public final int size() {
        int size = $size;
        if (size >= 0) {
            return size;
        }

        checkConsistent(keys, values);
        if (keys.length > 0) {
            size = 1;
            size += ulongSize(encodeLenPrefixedListLength(keys.length));
            for (int i = 0; i < keys.length; i++) {
                size += byteListLengthEncodedSize(keys[i].remaining());
            }
            size += sizeofValues();
        } else {
            size = 0;
        }

        this.$size = size;
        return size;
    }
}
