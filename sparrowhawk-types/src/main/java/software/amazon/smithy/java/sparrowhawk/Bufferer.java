/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public final class Bufferer {
    private static final int MAX_LENGTH_BYTES = 9;
    private final Consumer<byte[]> messageConsumer;

    private final byte[] lengthBytes = new byte[MAX_LENGTH_BYTES];
    private int lengthPos;
    private int lengthRemaining;

    private byte[] payload;
    private int payloadPos;

    public Bufferer(Consumer<byte[]> messageConsumer) {
        this.messageConsumer = messageConsumer;
    }

    public void feed(byte[] bytes) {
        feed(ByteBuffer.wrap(bytes));
    }

    public void feed(byte[] bytes, int offset, int length) {
        feed(ByteBuffer.wrap(bytes, offset, length));
    }

    public void feed(ByteBuffer byteBuffer) {
        while (byteBuffer.remaining() > 0) {
            if (payload == null) {
                if (!determineLength(byteBuffer)) {
                    return;
                }
                SparrowhawkDeserializer ds = new SparrowhawkDeserializer(lengthBytes);
                long len = KConstants.decodeByteListLength(ds.varUL());
                // payload length is size of the length prefix + that many bytes
                payload = new byte[(int) len + ds.pos()];
                payloadPos = ds.pos();
                System.arraycopy(lengthBytes, 0, payload, 0, payloadPos);
            }

            int toGet = Math.min(byteBuffer.remaining(), payload.length - payloadPos);
            byteBuffer.get(payload, payloadPos, toGet);
            payloadPos += toGet;
            if (payload.length == payloadPos) {
                byte[] p = payload;
                payload = null;
                lengthPos = 0;
                lengthRemaining = 0;
                payloadPos = 0;
                messageConsumer.accept(p);
            }
        }
    }

    private boolean determineLength(ByteBuffer byteBuffer) {
        if (lengthPos == 0) {
            byte firstLengthByte = byteBuffer.get();
            lengthBytes[lengthPos++] = firstLengthByte;
            if ((firstLengthByte & 1) == 1) {
                return true;
            }

            lengthRemaining = Integer.numberOfTrailingZeros(firstLengthByte | (1 << 8));
        }

        int readable = byteBuffer.remaining();
        if (readable == 0) {
            return false;
        }

        int toRead = Math.min(lengthRemaining, readable);
        byteBuffer.get(lengthBytes, lengthPos, toRead);
        lengthPos += toRead;
        lengthRemaining -= toRead;
        return lengthRemaining == 0;
    }
}
