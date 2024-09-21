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
