/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.pprofparser;

import com.google.perftools.profiles.ProfileProto.Function;
import com.google.perftools.profiles.ProfileProto.Location;
import com.google.perftools.profiles.ProfileProto.Mapping;
import com.google.perftools.profiles.ProfileProto.Profile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Id-based lookup over a pprof {@link Profile}. Unlike OTLP's index-based dictionary, pprof
 * references locations / functions / mappings by their explicit {@code id} field, so this builds the
 * {@code id -> message} maps once and resolves string-table indices.
 */
public final class PprofTables {

    private final List<String> stringTable;
    private final Map<Long, Location> locationsById;
    private final Map<Long, Function> functionsById;
    private final Map<Long, Mapping> mappingsById;

    public PprofTables(Profile profile) {
        this.stringTable = profile.getStringTableList();
        this.locationsById = new HashMap<>(profile.getLocationCount());
        for (Location location : profile.getLocationList()) {
            locationsById.put(location.getId(), location);
        }
        this.functionsById = new HashMap<>(profile.getFunctionCount());
        for (Function function : profile.getFunctionList()) {
            functionsById.put(function.getId(), function);
        }
        this.mappingsById = new HashMap<>(profile.getMappingCount());
        for (Mapping mapping : profile.getMappingList()) {
            mappingsById.put(mapping.getId(), mapping);
        }
    }

    /**
     * @return the string at the given string-table index, or an empty string for out-of-range
     * indices (the pprof convention is {@code string_table[0] == ""})
     */
    public String string(long index) {
        if (index < 0 || index >= stringTable.size()) {
            return "";
        }
        return stringTable.get((int) index);
    }

    public Location location(long id) {
        return locationsById.get(id);
    }

    public Function function(long id) {
        return functionsById.get(id);
    }

    public Mapping mapping(long id) {
        return mappingsById.get(id);
    }
}
