package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.intSize;

import java.util.List;

public final class SparseByteList extends SparseVarintList<Byte> {
    private static final SparseByteList EMPTY_LIST = emptyList(new SparseByteList(), new Byte[0]);

    public static SparseByteList fromList(List<Byte> l) {
        int length = l.size();
        if (length == 0) {
            return EMPTY_LIST;
        }

        SparseByteList list = new SparseByteList();
        list.list = l;
        return list;
    }

    @Override
    Byte[] newArray(int size) {
        return new Byte[size];
    }

    @Override
    Byte decode(SparrowhawkDeserializer d) {
        return d.varB();
    }

    @Override
    void encode(Byte value, SparrowhawkSerializer s) {
        byte b = value;
        s.writeVarUL(1 + intSize(b));
        s.writeRawByte(EXACTLY_ONE_VARINT_ENCODED);
        s.writeVarB(b);
    }

    @Override
    int size(Byte value) {
        return 2 + intSize(value);
    }
}
