package software.amazon.smithy.java.sparrowhawk;

import static software.amazon.smithy.java.sparrowhawk.KConstants.T_LIST;
import static software.amazon.smithy.java.sparrowhawk.KConstants.decodeElementCount;
import static software.amazon.smithy.java.sparrowhawk.KConstants.encodeByteListLength;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.byteListLengthEncodedSize;
import static software.amazon.smithy.java.sparrowhawk.SparrowhawkSerializer.ulongSize;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Supplier;


@SuppressWarnings("unchecked")
final class OptionalObject<T extends SparrowhawkObject> implements SparrowhawkObject {
    private static final long REQUIRED_LIST_0 = 0x0L;
    private long $list_0 = REQUIRED_LIST_0;
    // list fieldSet 0 index 1
    private static final long FIELD_ITEM = 0x8L;
    private Object object;

    private final Supplier<T> factory;

    public OptionalObject(Supplier<T> factory) {
        this.factory = factory;
    }

    public T getItem() {
        if (object instanceof ByteBuffer b) {
            SparrowhawkDeserializer deserializer = new SparrowhawkDeserializer(b);
            T obj = factory.get();
            obj.decodeFrom(deserializer);
            this.object = obj;
        }
        return (T) object;
    }

    public void setItem(T object) {
        if (object == null) {
            $list_0 &= ~FIELD_ITEM;
        } else {
            $list_0 |= FIELD_ITEM;
        }
        this.object = object;
        this.$size = -1;
    }

    public boolean hasItem() {
        return ($list_0 & FIELD_ITEM) != 0;
    }

    private int $size;

    public int size() {
        if ($size >= 0) {
            return $size;
        }

        int size = ($list_0 == 0x0L ? 0 : (ulongSize($list_0)));
        size += sizeListFields();
        this.$size = size;
        return size;
    }

    private int sizeListFields() {
        int size = 0;
        if (hasItem()) {
            if (object instanceof SparrowhawkObject) {
                size += byteListLengthEncodedSize(((T) object).size());
            } else {
                size += ((ByteBuffer) object).remaining();
            }
        }
        return size;
    }

    public void encodeTo(SparrowhawkSerializer s) {
        s.writeVarUL(encodeByteListLength(size()));
        writeListFields(s);
    }

    private void writeListFields(SparrowhawkSerializer s) {
        if ($list_0 != 0x0L) {
            s.writeVarUL($list_0);
            if (hasItem()) {
                if (object instanceof SparrowhawkObject k) {
                    k.encodeTo(s);
                } else {
                    s.writeEncodedObject((ByteBuffer) object);
                }
            }
        }
    }

    public void decodeFrom(SparrowhawkDeserializer d) {
        int size = (int) decodeElementCount(d.varUI());
        this.$size = size;
        int start = d.pos();

        while ((d.pos() - start) < size) {
            long fieldSet = d.varUL();
            int fieldSetIdx = ((fieldSet & 0b100) != 0) ? d.varUI() + 1 : 0;
            int type = (int) (fieldSet & 3);
            if (type == T_LIST) {
                if (fieldSetIdx != 0) { throw new IllegalArgumentException("unknown fieldSetIdx " + fieldSetIdx); }
                decodeListFieldSet0(d, fieldSet);
            } else {
                throw new RuntimeException("Unexpected field set type: " + type);
            }
        }
    }

    private void decodeListFieldSet0(SparrowhawkDeserializer d, long fieldSet) {
        this.$list_0 = fieldSet;
        if (hasItem()) {
            this.object = d.object();
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OptionalObject<?> o)) return false;
        return Objects.equals(getItem(), o.getItem());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getItem());
    }
}
