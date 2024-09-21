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
