package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeLenPrefixedListLength;
import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeVarintListLength;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.intSize;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.ulongSize;

import java.util.List;

public final class IntegerListMap extends NumberMap<List<Integer>> {
    @Override
    protected int decodeValueCount(int encodedCount) {
        return KConstants.decodeLenPrefixedListLengthChecked(encodedCount);
    }

    @Override
    protected List<Integer> decode(SparrowhawkDeserializer d) {
        return new IntegerList(d);
    }

    @Override
    protected int sizeofValues(List<Integer>[] elements) {
        int size = ulongSize(encodeLenPrefixedListLength(elements.length));
        for (List<Integer> list : elements) {
            size += ulongSize(encodeVarintListLength(list.size()));
            for (Integer val : list) {
                size += intSize(val);
            }
        }
        return size;
    }

    @Override
    protected List<Integer>[] newArray(int len) {
        return new List[len];
    }

    @Override
    protected void writeValues(SparrowhawkSerializer s, List<Integer>[] values) {
        s.writeVarUL(encodeLenPrefixedListLength(values.length));
        for (List<Integer> list : values) {
            s.writeVarUL(encodeVarintListLength(list.size()));
            for (Integer val : list) {
                s.writeVarI(val);
            }
        }
    }
}
