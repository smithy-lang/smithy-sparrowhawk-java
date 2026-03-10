package software.amazon.smithy.java.sparrowhawk;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public final class CopiedBytesMap extends AbstractBytesMap<ByteBuffer> {
    @Override
    ByteBuffer valueToBuffer(ByteBuffer value) {
        return value;
    }

    @Override
    public Map<String, ByteBuffer> toMap() {
        int sz = keys.length;
        Map<String, ByteBuffer> m = new HashMap<>(sz / 3 * 4);
        for (int i = 0; i < sz; i++) {
            m.put(string(keys[i]), values[i]);
        }
        return m;
    }

    @Override
    ByteBuffer readBytes(SparrowhawkDeserializer d) {
        return d.bytesCopied();
    }
}
