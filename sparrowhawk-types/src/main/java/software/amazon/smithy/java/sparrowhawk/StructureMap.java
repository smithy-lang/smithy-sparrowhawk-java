package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeElementCount;
import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeByteListLength;
import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeLenPrefixedListLength;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.EMPTY_LIST_SIZE_VARINT;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.byteListLengthEncodedSize;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.uintSize;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.ulongSize;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@SuppressWarnings("unchecked,rawtypes")
public final class StructureMap<T extends SparrowhawkObject> extends SparrowhawkMap<T> implements SparrowhawkObject {
    private static final long REQUIRED_LIST_FIELDSET_0 = KConstants.listField(0b11);
    private static final ByteBuffer[] EMPTY_KEYS = new ByteBuffer[0];
    private static final SparrowhawkObject[] EMPTY_VALUES = new SparrowhawkObject[0];

    private ByteBuffer[] keys;
    private SparrowhawkObject[] values;
    private final Supplier<SparrowhawkObject> factory;

    public StructureMap(Supplier<SparrowhawkObject> factory) {
        this.factory = factory;
    }

    @Override
    public Map<String, T> toMap() {
        int sz = keys.length;
        if (sz == 0) {
            return Collections.emptyMap();
        }

        SparrowhawkObject[] values = this.values;
        Map m = new HashMap<>(sz / 3 * 4);
        for (int i = 0; i < sz; i++) {
            m.put(string(keys[i]), values[i]);
        }
        return m;
    }

    public Map<String, T> toNestedMap(int depth) {
        int sz = keys.length;
        Map m = new HashMap<>(sz / 3 * 4);
        for (int i = 0; i < sz; i++) {
            Object val;
            if (depth == 1) {
                val = ((SparrowhawkMap) values[i]).toMap();
            } else {
                val = ((StructureMap) values[i]).toNestedMap(depth - 1);
            }
            m.put(string(keys[i]), val);
        }
        return m;
    }

    private static String string(ByteBuffer b) {
        return new String(b.array(), b.arrayOffset() + b.position(), b.remaining(), StandardCharsets.UTF_8);
    }

    public void fromNestedMap(Map<String, ?> map, int depth, Supplier<SparrowhawkMap<?>> supp) {
        int len = map.size();
        if (len == 0) {
            keys = EMPTY_KEYS;
            values = EMPTY_VALUES;
            $size = 0;
            return;
        }

        SparrowhawkObject[] values = new SparrowhawkObject[len];
        ByteBuffer[] keys = new ByteBuffer[len];
        int size = 1 + (2 * uintSize(encodeLenPrefixedListLength(len)));
        this.keys = keys;
        int i = 0;
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            byte[] key = entry.getKey().getBytes(StandardCharsets.UTF_8);
            keys[i] = ByteBuffer.wrap(key);
            Map value = (Map) entry.getValue();
            SparrowhawkObject inner;
            if (depth == 1) {
                SparrowhawkMap<?> m = supp.get();
                m.fromMap(value);
                inner = m;
            } else {
                StructureMap sm = new StructureMap(null);
                sm.fromNestedMap(value, depth - 1, supp);
                inner = sm;
            }

            values[i++] = inner;
            size += byteListLengthEncodedSize(key.length) + byteListLengthEncodedSize(inner.size());
        }
        this.values = values;
        this.$size = size;
    }

    @Override
    public void fromMap(Map<String, T> map) {
        int len = map.size();
        if (len == 0) {
            keys = EMPTY_KEYS;
            values = EMPTY_VALUES;
            $size = 0;
            return;
        }

        ByteBuffer[] keys = new ByteBuffer[len];
        this.keys = keys;
        SparrowhawkObject[] values = new SparrowhawkObject[len];
        this.values = values;
        int i = 0;
        int size = 1 + (2 * uintSize(encodeLenPrefixedListLength(len)));
        for (Map.Entry<String, T> entry : map.entrySet()) {
            byte[] key = entry.getKey().getBytes(StandardCharsets.UTF_8);
            keys[i] = ByteBuffer.wrap(key);
            SparrowhawkObject value = entry.getValue();
            values[i++] = value;
            size += byteListLengthEncodedSize(key.length) + byteListLengthEncodedSize(value.size());
        }
        this.$size = size;
    }

    @Override
    public void decodeFrom(SparrowhawkDeserializer d) {
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
            keys = readKeys(d, nkeys);
            int nvalues = (int) decodeElementCount(d.varUI());
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

    private SparrowhawkObject[] readValues(SparrowhawkDeserializer d, int n) {
        SparrowhawkObject[] values = new SparrowhawkObject[n];
        for (int i = 0; i < values.length; i++) {
            SparrowhawkObject obj = factory.get();
            obj.decodeFrom(d);
            values[i] = obj;
        }
        return values;
    }

    @Override
    public void encodeTo(SparrowhawkSerializer s) {
        int size = size();
        if (size > 0) {
            s.writeVarUL(encodeByteListLength(size));
            s.writeVarUL(REQUIRED_LIST_FIELDSET_0);
            long dl = encodeLenPrefixedListLength(keys.length);
            s.writeVarUL(dl);
            for (int i = 0; i < keys.length; i++) {
                s.writeBytes(keys[i]);
            }
            s.writeVarUL(dl);
            SparrowhawkObject[] values = this.values;
            for (int i = 0; i < values.length; i++) {
                values[i].encodeTo(s);
            }
        } else {
            s.writeRawByte(EMPTY_LIST_SIZE_VARINT);
        }
    }

    private transient int $size;

    @Override
    public int size() {
        int size = $size;
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
            SparrowhawkObject[] values = this.values;
            for (int i = 0; i < values.length; i++) {
                size += byteListLengthEncodedSize(values[i].size());
            }
        } else {
            size = 0;
        }

        this.$size = size;
        return size;
    }

    private static int invalidMap() {
        throw new RuntimeException("invalid map");
    }
}
