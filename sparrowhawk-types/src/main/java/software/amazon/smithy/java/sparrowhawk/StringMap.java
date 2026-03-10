package software.amazon.smithy.java.sparrowhawk;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


public final class StringMap extends AbstractBytesMap<String> {
    @Override
    ByteBuffer valueToBuffer(String value) {
        return ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Map<String, String> toMap() {
        int sz = keys.length;
        Map<String, String> m = new HashMap<>(sz / 3 * 4);
        for (int i = 0; i < sz; i++) {
            m.put(string(keys[i]), string(values[i]));
        }
        return m;
    }
}
