package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.longSize;

import java.util.List;

public final class SparseLongList extends SparseVarintList<Long> {
    private static final SparseLongList EMPTY_LIST = emptyList(new SparseLongList(), new Long[0]);

    public static SparseLongList fromList(List<Long> l) {
        int length = l.size();
        if (length == 0) {
            return EMPTY_LIST;
        }

        SparseLongList list = new SparseLongList();
        list.list = l;
        return list;
    }

    @Override
    Long[] newArray(int size) {
        return new Long[size];
    }

    @Override
    Long decode(SparrowhawkDeserializer d) {
        return d.varL();
    }

    @Override
    void encode(Long value, SparrowhawkSerializer s) {
        long v = value;
        s.writeVarUL(1 + longSize(v));
        s.writeRawByte(EXACTLY_ONE_VARINT_ENCODED);
        s.writeVarL(v);
    }

    @Override
    int size(Long value) {
        return 2 + longSize(value);
    }
}
