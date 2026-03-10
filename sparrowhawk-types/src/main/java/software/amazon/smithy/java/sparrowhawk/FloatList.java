package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeFourByteListLengthChecked;

import java.util.AbstractList;
import java.util.RandomAccess;

final class FloatList extends AbstractList<Float> implements RandomAccess {
    private final float[] list;

    public FloatList(SparrowhawkDeserializer d) {
        int sz = decodeFourByteListLengthChecked(d.varUL());
        float[] list = new float[sz];
        for (int i = 0; i < sz; i++) {
            list[i] = d.f4();
        }
        this.list = list;
    }

    @Override
    public Float get(int index) {
        return list[index];
    }

    @Override
    public int size() {
        return list.length;
    }
}
