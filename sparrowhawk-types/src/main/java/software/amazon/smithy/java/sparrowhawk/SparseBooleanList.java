package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeByteListLength;

import java.util.List;

public final class SparseBooleanList extends SparseVarintList<Boolean> {
    private static final SparseBooleanList EMPTY_LIST = emptyList(new SparseBooleanList(), new Boolean[0]);
    private static final byte[] BOOL_TRUE = serialize(true);
    private static final byte[] BOOL_FALSE = serialize(false);

    private static byte[] serialize(boolean b) {
        SparrowhawkSerializer s = new SparrowhawkSerializer(2);
        s.writeVarUL(encodeByteListLength(2));
        s.writeVarUL(EXACTLY_ONE_VARINT);
        s.writeBool(b);
        return s.payload();
    }

    public static SparseBooleanList fromList(List<Boolean> l) {
        int length = l.size();
        if (length == 0) {
            return EMPTY_LIST;
        }

        SparseBooleanList list = new SparseBooleanList();
        list.list = l;
        return list;
    }

    @Override
    Boolean[] newArray(int size) {
        return new Boolean[size];
    }

    @Override
    Boolean decode(SparrowhawkDeserializer d) {
        return d.varB() != 0;
    }

    @Override
    void encode(Boolean value, SparrowhawkSerializer s) {
        s.writeEncodedObject(value ? BOOL_TRUE : BOOL_FALSE, 0, 3);
    }

    @Override
    int size(Boolean value) {
        return 3;
    }
}
