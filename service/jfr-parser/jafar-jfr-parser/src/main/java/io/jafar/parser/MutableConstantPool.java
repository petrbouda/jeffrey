/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
