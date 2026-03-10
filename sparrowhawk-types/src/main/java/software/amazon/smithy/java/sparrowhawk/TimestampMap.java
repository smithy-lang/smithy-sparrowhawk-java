package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeFourBListLength;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.ulongSize;

import java.util.Date;

public final class TimestampMap extends NumberMap<Date> {
    @Override
    protected Date[] newArray(int len) {
        return new Date[len];
    }

    @Override
    protected int decodeValueCount(int encodedCount) {
        return KConstants.decodeEightByteListLengthChecked(encodedCount);
    }

    @Override
    protected Date decode(SparrowhawkDeserializer d) {
        return d.date();
    }

    @Override
    protected void writeValues(SparrowhawkSerializer s, Date[] values) {
        s.writeDateList(values);
    }

    @Override
    protected int sizeofValues(Date[] elements) {
        int n = elements.length;
        int size = ulongSize(encodeFourBListLength(n));
        return size + (8 * n);
    }
}
