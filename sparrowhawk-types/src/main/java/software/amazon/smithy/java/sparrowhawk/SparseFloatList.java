package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeByteListLength;
import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeLenPrefixedListLengthChecked;
import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeLenPrefixedListLength;

import java.util.List;

public final class SparseFloatList extends SparseList<Float> implements SparrowhawkObject {
    private static final SparseFloatList EMPTY_LIST = new SparseFloatList();
    private static final long EXACTLY_ONE_FLOAT = KConstants.fourField(1);
    private static final byte EXACTLY_ONE_FLOAT_ENCODED = (byte) (2 * EXACTLY_ONE_FLOAT + 1);

    static {
        EMPTY_LIST.list = new UnmodifiableArrayList<>(new Float[0]);
        EMPTY_LIST.size = 0;
    }

    private List<Float> list;

    public static SparseFloatList fromList(List<Float> l) {
        int length = l.size();
        if (length == 0) {
            return EMPTY_LIST;
        }

        SparseFloatList list = new SparseFloatList();
        list.list = l;
        return list;
    }

    @Override
    public void decodeFrom(SparrowhawkDeserializer d) {
        int sz = decodeLenPrefixedListLengthChecked(d.varUL());
        Float[] list = new Float[sz];
        for (int i = 0; i < sz; i++) {
            long size = decodeByteListLength(d.varUI());
            if (size != 0) {
                long fs = d.varUL();
                if (fs != EXACTLY_ONE_FLOAT) {
                    invalidFieldset(fs);
                }
                list[i] = d.f4();
            }
        }

        this.list = new UnmodifiableArrayList<>(list);
    }

    @Override
    public void encodeTo(SparrowhawkSerializer s) {
        s.writeVarUL(encodeLenPrefixedListLength(list.size()));
        for (int i = 0; i < list.size(); i++) {
            Float value = list.get(i);
            if (value == null) {
                s.writeEmptyObject();
            } else {
                s.writeRawByte((byte) (2 * 4 + 1));
                s.writeRawByte(EXACTLY_ONE_FLOAT_ENCODED);
                s.writeFloat(value);
            }
        }
    }

    @Override
    public int size() {
        int size = this.size;
        if (size >= 0) {
            return size;
        }

        size = 0;
        for (int i = 0; i < list.size(); i++) {
            size += list.get(i) == null ? 1 : 6;
        }

        return (this.size = size);
    }

    @Override
    public List<Float> toList() {
        return list;
    }

    @Override
    public int elementCount() {
        return list.size();
    }
}
