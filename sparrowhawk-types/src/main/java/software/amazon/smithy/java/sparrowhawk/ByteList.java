package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeVarintListLengthChecked;

import java.util.AbstractList;
import java.util.RandomAccess;

final class ByteList extends AbstractList<Byte> implements RandomAccess {
    private final byte[] list;

    public ByteList(SparrowhawkDeserializer d) {
        int sz = decodeVarintListLengthChecked(d.varUL());
        byte[] list = new byte[sz];
        for (int i = 0; i < sz; i++) {
            list[i] = d.varB();
        }
        this.list = list;
    }

    @Override
    public Byte get(int index) {
        return list[index];
    }

    @Override
    public int size() {
        return list.length;
    }
}
