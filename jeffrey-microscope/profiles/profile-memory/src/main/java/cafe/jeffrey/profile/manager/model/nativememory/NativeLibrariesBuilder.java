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
