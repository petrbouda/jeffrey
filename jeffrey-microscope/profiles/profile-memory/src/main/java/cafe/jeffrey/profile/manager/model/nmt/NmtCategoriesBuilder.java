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

package cafe.jeffrey.profile.manager.model.nmt;

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
 * Aggregates {@code jdk.NativeMemoryUsage} samples by category ({@code type}), keeping the first
 * committed value (for growth) and the latest reserved/committed values. Requires a chronological
 * stream. Result is ordered by committed bytes descending.
 */
public class NmtCategoriesBuilder implements RecordBuilder<GenericRecord, List<NmtCategory>> {

    private static final String TYPE_FIELD = "type";
    private static final String RESERVED_FIELD = "reserved";
    private static final String COMMITTED_FIELD = "committed";
    private static final String UNKNOWN_CATEGORY = "Unknown";

    private static final class Accumulator {
        private long startCommitted;
        private boolean startSeen;
        private long lastReserved;
        private long lastCommitted;
    }

    private final Map<String, Accumulator> accumulatorsByCategory = new LinkedHashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String category = Json.readString(fields, TYPE_FIELD);
        if (category == null) {
            category = UNKNOWN_CATEGORY;
        }
        long reserved = Math.max(0, Json.readLong(fields, RESERVED_FIELD));
        long committed = Math.max(0, Json.readLong(fields, COMMITTED_FIELD));

        Accumulator accumulator = accumulatorsByCategory.computeIfAbsent(category, key -> new Accumulator());
        if (!accumulator.startSeen) {
            accumulator.startCommitted = committed;
            accumulator.startSeen = true;
        }
        accumulator.lastReserved = reserved;
        accumulator.lastCommitted = committed;
    }

    @Override
    public List<NmtCategory> build() {
        List<NmtCategory> result = new ArrayList<>(accumulatorsByCategory.size());
        accumulatorsByCategory.forEach((category, accumulator) -> result.add(new NmtCategory(
                category,
                accumulator.lastReserved,
                accumulator.lastCommitted,
                accumulator.startCommitted,
                accumulator.lastCommitted - accumulator.startCommitted)));
        result.sort(Comparator.comparingLong(NmtCategory::committedBytes).reversed());
        return result;
    }
}
