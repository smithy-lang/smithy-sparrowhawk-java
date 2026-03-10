package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeVarintListLengthChecked;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.PrimitiveIterator;
import java.util.RandomAccess;
import java.util.Spliterator;

final class IntegerList extends AbstractList<Integer> implements RandomAccess {
    private final int[] list;

    public IntegerList(SparrowhawkDeserializer d) {
        int sz = decodeVarintListLengthChecked(d.varUL());
        int[] list = new int[sz];
        for (int i = 0; i < sz; i++) {
            list[i] = d.varI();
        }
        this.list = list;
    }

    @Override
    public Integer get(int index) {
        return list[index];
    }

    @Override
    public int size() {
        return list.length;
    }

    @Override
    public Spliterator.OfInt spliterator() {
        return Arrays.spliterator(list);
    }

    @Override
    public PrimitiveIterator.OfInt iterator() {
        return new IntIterator();
    }

    final class IntIterator implements PrimitiveIterator.OfInt {
        private int offset;

        @Override
        public int nextInt() {
            return list[offset++];
        }

        @Override
        public boolean hasNext() {
            return offset < list.length;
        }
    }
}
