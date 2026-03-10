package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.intSize;

import java.util.List;

public final class SparseShortList extends SparseVarintList<Short> {
    private static final SparseShortList EMPTY_LIST = emptyList(new SparseShortList(), new Short[0]);

    public static SparseShortList fromList(List<Short> l) {
        int length = l.size();
        if (length == 0) {
            return EMPTY_LIST;
        }

        SparseShortList list = new SparseShortList();
        list.list = l;
        return list;
    }

    @Override
    Short[] newArray(int size) {
        return new Short[size];
    }

    @Override
    Short decode(SparrowhawkDeserializer d) {
        return d.varS();
    }

    @Override
    void encode(Short value, SparrowhawkSerializer s) {
        short v = value;
        s.writeVarUL(1 + intSize(v));
        s.writeRawByte(EXACTLY_ONE_VARINT_ENCODED);
        s.writeVarS(v);
    }

    @Override
    int size(Short value) {
        return 2 + intSize(value);
    }
}
