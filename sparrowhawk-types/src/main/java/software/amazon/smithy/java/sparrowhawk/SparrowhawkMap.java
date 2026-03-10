package software.amazon.smithy.java.sparrowhawk;

import java.util.Map;

public abstract class SparrowhawkMap<T> implements SparrowhawkObject {
    public abstract void fromMap(Map<String, T> map);

    public abstract Map<String, T> toMap();
}
