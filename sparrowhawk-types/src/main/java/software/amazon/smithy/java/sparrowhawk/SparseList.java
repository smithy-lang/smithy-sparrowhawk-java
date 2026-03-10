package software.amazon.smithy.java.sparrowhawk;

import java.util.List;

public abstract class SparseList<T> implements SparrowhawkObject {
    int size = -1;

    public abstract List<T> toList();

    public abstract int elementCount();

    static void invalidFieldset(long fs) {
        throw new ParseException("Expected exactly one varint, but got 0b" + Long.toBinaryString(fs));
    }

    static void wrongSize(long size) {
        throw new ParseException("Sparse list entry is wrong size: " + size);
    }
}
