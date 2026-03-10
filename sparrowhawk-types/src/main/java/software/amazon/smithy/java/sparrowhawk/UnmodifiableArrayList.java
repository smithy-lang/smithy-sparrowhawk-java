package software.amazon.smithy.java.sparrowhawk;

import java.util.AbstractList;

final class UnmodifiableArrayList<T> extends AbstractList<T> {
    private final T[] list;

    UnmodifiableArrayList(T[] list) {
        this.list = list;
    }

    @Override
    public T get(int index) {
        return list[index];
    }

    @Override
    public int size() {
        return list.length;
    }
}
