

package io.jafar.parser;

import io.jafar.parser.internal_api.MetadataLookup;
import io.jafar.parser.internal_api.metadata.MetadataClass;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Arrays;

public final class MutableMetadataLookup implements MetadataLookup {
    private String[] strings;
    private final Long2ObjectMap<MetadataClass> classes = new Long2ObjectOpenHashMap<>();

    @Override
    public String getString(int idx) {
        return strings[idx];
    }

    @Override
    public MetadataClass getClass(long id) {
        return classes.get(id);
    }

    public MetadataClass addClass(long id, MetadataClass clazz) {
        return classes.computeIfAbsent(id, k -> clazz);
    }
    public void setStringtable(String[] stringTable) {
        this.strings = Arrays.copyOf(stringTable, stringTable.length);
    }

    public void clear() {
        strings = null;
        classes.clear();
    }
}
