package pbouda.jeffrey.provider.api.model.writer;

import org.eclipse.collections.api.factory.primitive.LongSets;
import org.eclipse.collections.api.list.primitive.LongList;
import org.eclipse.collections.api.set.primitive.MutableLongSet;

public class LongDeduplicator {

    private final MutableLongSet used = LongSets.mutable.empty();

    public LongList checkAndAdd(LongList values) {
       return values.select(this::checkAndAdd);
    }

    public boolean checkAndAdd(long value) {
        return used.add(value);
    }
}
