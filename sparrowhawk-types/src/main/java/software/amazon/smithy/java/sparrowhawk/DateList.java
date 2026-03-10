package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeEightByteListLengthChecked;

import java.util.AbstractList;
import java.util.Date;
import java.util.RandomAccess;

final class DateList extends AbstractList<Date> implements RandomAccess {
    private final Date[] list;

    DateList(SparrowhawkDeserializer d) {
        int sz = decodeEightByteListLengthChecked(d.varUL());
        Date[] list = new Date[sz];
        for (int i = 0; i < sz; i++) {
            list[i] = d.date();
        }
        this.list = list;
    }

    @Override
    public Date get(int index) {
        return list[index];
    }

    @Override
    public int size() {
        return list.length;
    }
}
