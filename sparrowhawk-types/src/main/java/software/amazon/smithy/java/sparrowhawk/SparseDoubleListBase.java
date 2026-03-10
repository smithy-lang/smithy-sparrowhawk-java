package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeByteListLength;
import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeLenPrefixedListLengthChecked;
import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeLenPrefixedListLength;

import java.util.List;

abstract class SparseDoubleListBase<T> extends SparseList<T> {
    private static final long EXACTLY_ONE_DOUBLE = KConstants.eightField(1);
    private static final byte EXACTLY_ONE_DOUBLE_ENCODED = (byte) (2 * EXACTLY_ONE_DOUBLE + 1);

    @Override
    public final void decodeFrom(SparrowhawkDeserializer d) {
        int sz = decodeLenPrefixedListLengthChecked(d.varUL());
        T[] list = newArray(sz);
        for (int i = 0; i < sz; i++) {
            long size = decodeByteListLength(d.varUI());
            if (size != 0) {
                long fs = d.varUL();
                if (fs != EXACTLY_ONE_DOUBLE) {
                    invalidFieldset(fs);
                }
                list[i] = decode(d);
            }
        }

        this.list = new UnmodifiableArrayList<>(list);
    }

    abstract T[] newArray(int size);

    abstract T decode(SparrowhawkDeserializer d);

    abstract void encode(T value, SparrowhawkSerializer s);

    List<T> list;

    @Override
    public final void encodeTo(SparrowhawkSerializer s) {
        s.writeVarUL(encodeLenPrefixedListLength(list.size()));
        for (int i = 0; i < list.size(); i++) {
            T value = list.get(i);
            if (value == null) {
                s.writeEmptyObject();
            } else {
                s.writeRawByte((byte) (2 * 8 + 1));
                s.writeRawByte(EXACTLY_ONE_DOUBLE_ENCODED);
                encode(value, s);
            }
        }
    }

    @Override
    public final int size() {
        int size = this.size;
        if (size >= 0) {
            return size;
        }

        size = 0;
        for (int i = 0; i < list.size(); i++) {
            size += list.get(i) == null ? 1 : 10;
        }

        return (this.size = size);
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
