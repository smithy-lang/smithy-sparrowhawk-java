package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.intSize;

import java.util.List;

public final class SparseIntegerList extends SparseVarintList<Integer> {
    private static final SparseIntegerList EMPTY_LIST = emptyList(new SparseIntegerList(), new Integer[0]);

    public static SparseIntegerList fromList(List<Integer> l) {
        int length = l.size();
        if (length == 0) {
            return EMPTY_LIST;
        }

        SparseIntegerList list = new SparseIntegerList();
        list.list = l;
        return list;
    }

    @Override
    Integer[] newArray(int size) {
        return new Integer[size];
    }

    @Override
    Integer decode(SparrowhawkDeserializer d) {
        return d.varI();
    }

    @Override
    void encode(Integer value, SparrowhawkSerializer s) {
        int v = value;
        s.writeVarUL(1 + intSize(v));
        s.writeRawByte(EXACTLY_ONE_VARINT_ENCODED);
        s.writeVarI(v);
    }

    @Override
    int size(Integer value) {
        return 2 + intSize(value);
    }
}
