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
