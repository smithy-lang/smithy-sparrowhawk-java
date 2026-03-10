package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeVarintListLengthChecked;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.PrimitiveIterator;
import java.util.RandomAccess;
import java.util.Spliterator;

final class LongList extends AbstractList<Long> implements RandomAccess {
    private final long[] list;

    public LongList(SparrowhawkDeserializer d) {
        int sz = decodeVarintListLengthChecked(d.varUL());
        long[] list = new long[sz];
        for (int i = 0; i < sz; i++) {
            list[i] = d.varL();
        }
        this.list = list;
    }

    @Override
    public Long get(int index) {
        return list[index];
    }

    @Override
    public int size() {
        return list.length;
    }

    @Override
    public Spliterator.OfLong spliterator() {
        return Arrays.spliterator(list);
    }

    @Override
    public PrimitiveIterator.OfLong iterator() {
        return new LongIterator();
    }

    final class LongIterator implements PrimitiveIterator.OfLong {
        private int offset;

        @Override
        public long nextLong() {
            return list[offset++];
        }

        @Override
        public boolean hasNext() {
            return offset < list.length;
        }
    }
}
