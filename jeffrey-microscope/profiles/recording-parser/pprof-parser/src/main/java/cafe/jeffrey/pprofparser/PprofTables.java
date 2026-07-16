/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
