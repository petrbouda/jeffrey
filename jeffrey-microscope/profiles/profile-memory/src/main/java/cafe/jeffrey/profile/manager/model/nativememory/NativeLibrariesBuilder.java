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

package cafe.jeffrey.profile.manager.model.nativememory;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collapses periodic {@code jdk.NativeLibrary} snapshots into one row per library (keyed by name,
 * last snapshot wins), ordered by descending mapped size.
 */
public class NativeLibrariesBuilder implements RecordBuilder<GenericRecord, List<NativeLibraryInfo>> {

    private static final String NAME_FIELD = "name";
    private static final String BASE_ADDRESS_FIELD = "baseAddress";
    private static final String TOP_ADDRESS_FIELD = "topAddress";

    private final Map<String, NativeLibraryInfo> librariesByName = new LinkedHashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String name = Json.readString(fields, NAME_FIELD);
        if (name == null) {
            return;
        }

        long baseAddress = Json.readLong(fields, BASE_ADDRESS_FIELD);
        long topAddress = Json.readLong(fields, TOP_ADDRESS_FIELD);
        long mappedBytes = (baseAddress >= 0 && topAddress > baseAddress) ? topAddress - baseAddress : 0;

        librariesByName.put(name, new NativeLibraryInfo(name, mappedBytes));
    }

    @Override
    public List<NativeLibraryInfo> build() {
        List<NativeLibraryInfo> result = new ArrayList<>(librariesByName.values());
        result.sort(Comparator.comparingLong(NativeLibraryInfo::mappedBytes).reversed());
        return result;
    }
}
