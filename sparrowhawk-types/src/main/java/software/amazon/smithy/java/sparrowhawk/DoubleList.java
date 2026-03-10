package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeEightByteListLengthChecked;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.PrimitiveIterator;
import java.util.RandomAccess;
import java.util.Spliterator;

final class DoubleList extends AbstractList<Double> implements RandomAccess {
    private final double[] list;

    public DoubleList(SparrowhawkDeserializer d) {
        int sz = decodeEightByteListLengthChecked(d.varUL());
        double[] list = new double[sz];
        for (int i = 0; i < sz; i++) {
            list[i] = d.d8();
        }
        this.list = list;
    }

    @Override
    public Double get(int index) {
        return list[index];
    }

    @Override
    public int size() {
        return list.length;
    }

    @Override
    public Spliterator.OfDouble spliterator() {
        return Arrays.spliterator(list);
    }

    @Override
    public PrimitiveIterator.OfDouble iterator() {
        return new DoubleIterator();
    }

    final class DoubleIterator implements PrimitiveIterator.OfDouble {
        private int offset;

        @Override
        public double nextDouble() {
            return list[offset++];
        }

        @Override
        public boolean hasNext() {
            return offset < list.length;
        }
    }
}
