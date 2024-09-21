

package io.jafar.parser;

import io.jafar.parser.internal_api.ConstantPool;
import io.jafar.parser.internal_api.DeserializationHandler;
import io.jafar.parser.internal_api.RecordingStream;
import io.jafar.parser.internal_api.metadata.MetadataClass;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public final class MutableConstantPool implements ConstantPool {
    private final Long2IntMap offsets = new Long2IntOpenHashMap();
    private final Long2ObjectMap<Object> entries = new Long2ObjectOpenHashMap<>();

    private final RecordingStream stream;
    private final MetadataClass clazz;
    private final DeserializationHandler<?> handler;

    public MutableConstantPool(RecordingStream chunkStream, long typeId) {
        this.stream = chunkStream;
        var context = chunkStream.getContext();
        clazz = context.getMetadataLookup().getClass(typeId);
        this.handler = context.getDeserializers().getDeserializer(clazz.getName());
    }

    public Object get(long id) {
        int offset = offsets.get(id);
        if (offset > 0) {
            return entries.computeIfAbsent(id, k -> {
                int pos = stream.position();
                try {
                    stream.position(offset);
                    return handler.handle(stream);
                } finally {
                    stream.position(pos);
                }
            });
        }
        return null;
    }

    public void addOffset(long id, int offset) {
        offsets.put(id, offset);
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public MetadataClass getType() {
        return clazz;
    }
}
