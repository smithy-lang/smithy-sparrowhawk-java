package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeByteListLength;
import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeLenPrefixedListLengthChecked;
import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeLenPrefixedListLength;

import java.util.List;

abstract class SparseVarintList<T> extends SparseList<T> {
    static final long EXACTLY_ONE_VARINT = KConstants.varintField(1);
    static final byte EXACTLY_ONE_VARINT_ENCODED = (byte) (2 * EXACTLY_ONE_VARINT + 1);

    static <V, T extends SparseVarintList<V>> T emptyList(T l, V[] arr) {
        l.size = 0;
        l.list = new UnmodifiableArrayList<>(arr);
        return l;
    }

    abstract T[] newArray(int size);

    abstract T decode(SparrowhawkDeserializer d);

    abstract void encode(T value, SparrowhawkSerializer s);

    abstract int size(T value);

    List<T> list;

    @Override
    public final void decodeFrom(SparrowhawkDeserializer d) {
        int sz = decodeLenPrefixedListLengthChecked(d.varUL());
        T[] list = newArray(sz);
        for (int i = 0; i < sz; i++) {
            long size = decodeByteListLength(d.varUI());
            if (size != 0) {
                long fs = d.varUL();
                if (fs != EXACTLY_ONE_VARINT) {
                    invalidFieldset(fs);
                }
                list[i] = decode(d);
            }
        }

        this.list = new UnmodifiableArrayList<>(list);
    }

    @Override
    public final void encodeTo(SparrowhawkSerializer s) {
        s.writeVarUL(encodeLenPrefixedListLength(list.size()));
        for (int i = 0; i < list.size(); i++) {
            T value = list.get(i);
            if (value == null) {
                s.writeEmptyObject();
            } else {
                encode(value, s);
            }
        }
    }

    @Override
    public final int size() {
        int size = this.size;
        if (size < 0) {
            int len = list.size();
            size = 0;
            for (int i = 0; i < len; i++) {
                T b = list.get(i);
                size += b == null ? 1 : size(b);
            }
            this.size = size;
        }

        return size;
    }

    @Override
    public final List<T> toList() {
        return list;
    }

    @Override
    public final int elementCount() {
        return list.size();
    }
}
