package software.amazon.smithy.java.sparrowhawk;

import java.nio.ByteBuffer;
import java.util.AbstractList;
import java.util.RandomAccess;

final class CopiedByteBufferList extends AbstractList<ByteBuffer> implements RandomAccess {
    private final ByteBuffer[] list;

    CopiedByteBufferList(SparrowhawkDeserializer d) {
        int sz = KConstants.decodeLenPrefixedListLengthChecked(d.varUL());
        ByteBuffer[] list = new ByteBuffer[sz];
        for (int i = 0; i < sz; i++) {
            list[i] = d.bytesCopied();
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
