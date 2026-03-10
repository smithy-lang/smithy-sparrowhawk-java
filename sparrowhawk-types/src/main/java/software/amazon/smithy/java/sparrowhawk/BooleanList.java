package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeVarintListLengthChecked;

import java.util.AbstractList;
import java.util.RandomAccess;

final class BooleanList extends AbstractList<Boolean> implements RandomAccess {
    private final boolean[] list;

    public BooleanList(SparrowhawkDeserializer d) {
        int sz = decodeVarintListLengthChecked(d.varUL());
        boolean[] list = new boolean[sz];
        for (int i = 0; i < sz; i++) {
            list[i] = d.bool();
        }
        this.list = list;
    }

    @Override
    public Boolean get(int index) {
        return list[index];
    }

    @Override
    public int size() {
        return list.length;
    }
}
