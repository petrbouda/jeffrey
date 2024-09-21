

package io.jafar.parser;

import io.jafar.parser.MutableConstantPool;
import io.jafar.parser.internal_api.ConstantPool;
import io.jafar.parser.internal_api.ConstantPools;
import io.jafar.parser.internal_api.MetadataLookup;
import io.jafar.parser.internal_api.RecordingStream;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.stream.Stream;

public final class MutableConstantPools implements ConstantPools {
    private final Long2ObjectMap<io.jafar.parser.MutableConstantPool> poolMap = new Long2ObjectOpenHashMap<>();

    private final MetadataLookup metadata;
    private boolean ready = false;

    public MutableConstantPools(MetadataLookup metadata) {
        this.metadata = metadata;
    }

    @Override
    public io.jafar.parser.MutableConstantPool getConstantPool(long typeId) {
        return poolMap.get(typeId);
    }

    public io.jafar.parser.MutableConstantPool addOrGetConstantPool(RecordingStream chunkStream, long typeId) {
        return poolMap.computeIfAbsent(typeId, k -> new MutableConstantPool(chunkStream, k));
    }

    @Override
    public boolean hasConstantPool(long typeId) {
        return poolMap.containsKey(typeId);
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    public void setReady() {
        ready = true;
    }

    @Override
    public Stream<? extends ConstantPool> pools() {
        return poolMap.values().stream();
    }
}
