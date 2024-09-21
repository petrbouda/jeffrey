

package io.jafar.parser.internal_api;

import io.jafar.parser.Deserializers;
import io.jafar.parser.MutableConstantPools;
import io.jafar.parser.MutableMetadataLookup;
import io.jafar.parser.TypeFilter;
import io.jafar.parser.internal_api.ConstantPools;
import io.jafar.parser.internal_api.MetadataLookup;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ParserContext {
    private final MutableMetadataLookup metadataLookup;
    private final MutableConstantPools constantPools;

    private final int chunkIndex;
    private volatile TypeFilter typeFilter;

    private final Deserializers deserializers = new Deserializers();

    private final ConcurrentMap<String, WeakReference<?>> bag = new ConcurrentHashMap<>();

    public ParserContext() {
        this.metadataLookup = new MutableMetadataLookup();
        this.constantPools = new MutableConstantPools(metadataLookup);

        this.typeFilter = null;
        this.chunkIndex = 0;
    }

    public ParserContext(TypeFilter typeFilter, int chunkIndex, MutableMetadataLookup metadataLookup, MutableConstantPools constantPools) {
        this.metadataLookup = metadataLookup;
        this.constantPools = constantPools;

        this.typeFilter = typeFilter;
        this.chunkIndex = chunkIndex;
    }

    public MetadataLookup getMetadataLookup() {
        return metadataLookup;
    }

    public ConstantPools getConstantPools() {
        return constantPools;
    }

    public TypeFilter getTypeFilter() {
        return typeFilter;
    }

    public void setTypeFilter(TypeFilter typeFilter) {
        this.typeFilter = typeFilter;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public Deserializers getDeserializers() {
        return deserializers;
    }

    public <T> void put(String key, Class<T> clz, T value) {
        bag.put(key, new WeakReference<>(value));
    }

    public <T> T get(String key, Class<T> clz) {
        return clz.cast(bag.get(key).get());
    }

    public void clear() {
        deserializers.clear();
    }
}
