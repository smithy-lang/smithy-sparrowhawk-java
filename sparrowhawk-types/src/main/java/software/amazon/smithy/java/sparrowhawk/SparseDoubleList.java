package software.amazon.smithy.java.sparrowhawk;

import java.util.List;

public final class SparseDoubleList extends SparseDoubleListBase<Double> {
    private static final SparseDoubleList EMPTY_LIST = new SparseDoubleList();

    static {
        EMPTY_LIST.list = new UnmodifiableArrayList<>(new Double[0]);
        EMPTY_LIST.size = 0;
    }

    public static SparseDoubleList fromList(List<Double> l) {
        int length = l.size();
        if (length == 0) {
            return EMPTY_LIST;
        }

        SparseDoubleList list = new SparseDoubleList();
        list.list = l;
        return list;
    }

    @Override
    Double[] newArray(int size) {
        return new Double[size];
    }

    @Override
    Double decode(SparrowhawkDeserializer d) {
        return d.d8();
    }

    @Override
    void encode(Double value, SparrowhawkSerializer s) {
        s.writeDouble(value);
    }
}
