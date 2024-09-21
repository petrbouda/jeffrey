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
