package software.amazon.smithy.java.sparrowhawk;

import java.util.Date;
import java.util.List;

public final class SparseTimestampList extends SparseDoubleListBase<Date> {
    private static final SparseTimestampList EMPTY_LIST = new SparseTimestampList();

    static {
        EMPTY_LIST.list = new UnmodifiableArrayList<>(new Date[0]);
        EMPTY_LIST.size = 0;
    }

    public static SparseTimestampList fromList(List<Date> l) {
        int length = l.size();
        if (length == 0) {
            return EMPTY_LIST;
        }

        SparseTimestampList list = new SparseTimestampList();
        list.list = l;
        return list;
    }

    @Override
    Date[] newArray(int size) {
        return new Date[size];
    }

    @Override
    Date decode(SparrowhawkDeserializer d) {
        return new Date(Math.round(d.d8() * 1000d));
    }

    @Override
    void encode(Date value, SparrowhawkSerializer s) {
        s.writeDate(value);
    }
}
