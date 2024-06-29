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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@SuppressWarnings("rawtypes,unchecked")
public final class SparseStructureMap<T extends SparrowhawkObject> implements SparrowhawkObject {
    private static final long REQUIRED_LIST_FIELDSET_0 = KConstants.listField(0b11);
    private static final ByteBuffer[] EMPTY_KEYS = new ByteBuffer[0];
    private static final OptionalObject[] EMPTY_VALUES = new OptionalObject[0];

    private ByteBuffer[] keys;
    private OptionalObject<T>[] values;
    private final Supplier<T> factory;

    public SparseStructureMap(Supplier<T> factory) {
        this.factory = factory;
    }

    public Map<String, T> toMap() {
        int sz = keys.length;
        OptionalObject<T>[] values = this.values;
        Map<String, T> m = new HashMap<>(sz / 3 * 4);
        for (int i = 0; i < sz; i++) {
            m.put(string(keys[i]), values[i].getItem());
        }
        return m;
    }


    private static String string(ByteBuffer b) {
        return new String(b.array(), b.arrayOffset() + b.position(), b.remaining(), StandardCharsets.UTF_8);
    }

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
        OptionalObject<T>[] values = (OptionalObject<T>[]) new OptionalObject[len];
        this.values = values;
        int i = 0;
        int size = 1 + (2 * uintSize(encodeLenPrefixedListLength(len)));
        for (Map.Entry<String, T> entry : map.entrySet()) {
            byte[] key = entry.getKey().getBytes(StandardCharsets.UTF_8);
            keys[i] = ByteBuffer.wrap(key);
            T value = entry.getValue();
            size += byteListLengthEncodedSize(key.length);
            if (value != null) {
                OptionalObject<T> obj = new OptionalObject<>(factory);
                obj.setItem(value);
                values[i] = obj;
                size += byteListLengthEncodedSize(obj.size());
            } else {
                size += 1;
            }
            i++;
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

    private OptionalObject<T>[] readValues(SparrowhawkDeserializer d, int n) {
        OptionalObject<T>[] values = new OptionalObject[n];
        for (int i = 0; i < values.length; i++) {
            // it would be nice to remove the object allocation and inline the decoding routine here
            OptionalObject<T> o = new OptionalObject<>(factory);
            o.decodeFrom(d);
            values[i] = o;
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
            OptionalObject[] values = this.values;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == null) {
                    s.writeRawByte((byte) 1); // list length of zero, indicating an empty OptionalObject
                } else {
                    values[i].encodeTo(s);
                }
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
            OptionalObject[] values = this.values;
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
