package software.amazon.smithy.java.sparrowhawk;

import java.nio.ByteBuffer;
import java.util.AbstractList;
import java.util.RandomAccess;

final class ByteBufferList extends AbstractList<ByteBuffer> implements RandomAccess {
    private final ByteBuffer[] list;

    ByteBufferList(SparrowhawkDeserializer d) {
        int sz = KConstants.decodeLenPrefixedListLengthChecked(d.varUL());
        ByteBuffer[] list = new ByteBuffer[sz];
        for (int i = 0; i < sz; i++) {
            list[i] = d.bytes();
        }
        this.list = list;
    }

    @Override
    public ByteBuffer get(int index) {
        return list[index];
    }

    @Override
    public int size() {
        return list.length;
    }
}
