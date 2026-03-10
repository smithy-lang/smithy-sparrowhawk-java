package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeVarintListLengthChecked;

import java.util.AbstractList;
import java.util.RandomAccess;

final class ShortList extends AbstractList<Short> implements RandomAccess {
    private final short[] list;

    public ShortList(SparrowhawkDeserializer d) {
        int sz = decodeVarintListLengthChecked(d.varUL());
        short[] list = new short[sz];
        for (int i = 0; i < sz; i++) {
            list[i] = d.varS();
        }
        this.list = list;
    }

    @Override
    public Short get(int index) {
        return list[index];
    }

    @Override
    public int size() {
        return list.length;
    }
}
