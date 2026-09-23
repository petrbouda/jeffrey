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

package cafe.jeffrey.profile.manager.model.allocation;

import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups allocation events by class and sums allocated bytes (event weight) and counts, returning
 * the top-N classes by bytes.
 */
public class AllocatedTypesBuilder implements RecordBuilder<GenericRecord, List<AllocatedType>> {

    private static final String OBJECT_CLASS_FIELD = "objectClass";
    private static final String UNKNOWN_CLASS = "<unknown>";

    private static final class Accumulator {
        private long bytes;
        private long count;
    }

    private final int maxEntries;
    private final Map<String, Accumulator> accumulatorsByClass = new HashMap<>();

    public AllocatedTypesBuilder(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive: " + maxEntries);
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public void onRecord(GenericRecord record) {
        String objectClass = Json.readString(record.jsonFields(), OBJECT_CLASS_FIELD);
        if (objectClass == null) {
            objectClass = UNKNOWN_CLASS;
        }
        Accumulator accumulator = accumulatorsByClass.computeIfAbsent(objectClass, key -> new Accumulator());
        accumulator.bytes += Math.max(0, record.sampleWeight());
        accumulator.count++;
    }

    @Override
    public List<AllocatedType> build() {
        List<AllocatedType> result = new ArrayList<>(accumulatorsByClass.size());
        accumulatorsByClass.forEach((className, accumulator) ->
                result.add(new AllocatedType(className, accumulator.bytes, accumulator.count)));
        result.sort(Comparator.comparingLong(AllocatedType::bytes).reversed());
        return result.size() > maxEntries ? List.copyOf(result.subList(0, maxEntries)) : result;
    }
}
