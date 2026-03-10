package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeElementCount;
import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeByteListLength;
import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeLenPrefixedListLength;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.byteListLengthEncodedSize;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.uintSize;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.ulongSize;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

abstract class AbstractBytesMap<T> extends SparrowhawkMap<T> implements SparrowhawkObject {
    private static final long REQUIRED_LIST_FIELDSET_0 = KConstants.listField(0b11);
    private static final ByteBuffer[] EMPTY = new ByteBuffer[0];

    int $size = -1;
    ByteBuffer[] keys;
    ByteBuffer[] values;

    @Override
    public final void fromMap(Map<String, T> m) {
        int len = m.size();
        if (len == 0) {
            keys = values = EMPTY;
            $size = 0;
            return;
        }

        ByteBuffer[] keys = new ByteBuffer[len];
        ByteBuffer[] values = new ByteBuffer[len];
        this.keys = keys;
        this.values = values;
        // fieldset + 2List lengths
        int size = 1 + (2 * uintSize(encodeLenPrefixedListLength(len)));
        size += encodeEntries(m, keys, values);
        $size = size;
    }

    private int encodeEntries(Map<String, T> m, ByteBuffer[] keys, ByteBuffer[] values) {
        if (keys.length != values.length) throw new IllegalArgumentException();
        if (keys.length == 0) return 0;
        Iterator<Map.Entry<String, T>> iter = m.entrySet().iterator();
        int s = 0;
        for (int i = 0; i < keys.length; i++) {
            Map.Entry<String, T> e = iter.next();
            s += encodeEntry(e, keys, values, i);
        }
        return s;
    }

    private int encodeEntry(Map.Entry<String, T> e, ByteBuffer[] keys, ByteBuffer[] values, int i) {
        byte[] key = encodeKey(e);
        ByteBuffer value = valueToBuffer(e.getValue());
        keys[i] = ByteBuffer.wrap(key);
        values[i] = value;
        return l(key, value);
    }

    private static <T> byte[] encodeKey(Map.Entry<String, T> e) {
        return e.getKey().getBytes(StandardCharsets.UTF_8);
    }

    abstract ByteBuffer valueToBuffer(T value);

    private static int l(byte[] key, ByteBuffer value) {
        return byteListLengthEncodedSize(key.length) + byteListLengthEncodedSize(value.remaining());
    }

    @Override
    public void decodeFrom(SparrowhawkDeserializer d) {
        $size = decodeElementCount(d.varUI());
        if ($size > 0) {
            long fieldset = d.varUL();
            if ((fieldset & 3) != KConstants.T_LIST) {
                throw new RuntimeException("bad field type: " + KConstants.fieldType(fieldset));
            }
            if ((fieldset & REQUIRED_LIST_FIELDSET_0) != REQUIRED_LIST_FIELDSET_0) {
                throw new RuntimeException("missing required fields");
            }
            int nkeys = decodeElementCount(d.varUI());
            keys = readBytes(d, nkeys);
            int nvalues = decodeElementCount(d.varUI());
            if (nkeys != nvalues) {
                throw new RuntimeException("mismatch in key and value lengths");
            }
            values = readBytes(d, nvalues);
        } else {
            keys = values = EMPTY;
        }
    }

    private ByteBuffer[] readBytes(SparrowhawkDeserializer d, int n) {
        ByteBuffer[] bs = new ByteBuffer[n];
        for (int i = 0; i < bs.length; i++) {
            bs[i] = readBytes(d);
        }
        return bs;
    }

    ByteBuffer readBytes(SparrowhawkDeserializer d) {
        return d.bytes();
    }

    @Override
    public void encodeTo(SparrowhawkSerializer s) {
        int size = size();
        s.writeVarUL(encodeByteListLength(size));
        if (size > 0) {
            s.writeVarUL(REQUIRED_LIST_FIELDSET_0);
            long dl = encodeLenPrefixedListLength(keys.length);
            s.writeVarUL(dl);
            for (int i = 0; i < keys.length; i++) {
                s.writeBytes(keys[i]);
            }
            s.writeVarUL(dl);
            for (int i = 0; i < values.length; i++) {
                s.writeBytes(values[i]);
            }
        }
    }

    @Override
    public int size() {
        int size = this.$size;
        if (size >= 0) {
            return size;
        }

        if (keys.length != values.length) {
            return invalidMap();
        }

        if (keys.length > 0) {
            size = 1; // required list field 0
            size += (2 * ulongSize(encodeLenPrefixedListLength(keys.length)));
            for (int i = 0; i < keys.length; i++) {
                size += byteListLengthEncodedSize(keys[i].remaining());
            }
            for (int i = 0; i < values.length; i++) {
                size += byteListLengthEncodedSize(values[i].remaining());
            }
        } else {
            size = 0; // 0 size written as 1 byte
        }

        this.$size = size;
        return size;
    }

    private static int invalidMap() {
        throw new RuntimeException("invalid map");
    }

    static String string(ByteBuffer b) {
        if (b.hasArray()) {
            return new String(b.array(), b.arrayOffset() + b.position(), b.remaining(), StandardCharsets.UTF_8);
        }
        return stringSlow(b);
    }

    private static String stringSlow(ByteBuffer b) {
        byte[] bytes = new byte[b.remaining()];
        b.duplicate().get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
