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

package cafe.jeffrey.profile.manager.model.gc.tuning;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.manager.model.gc.tuning.IhopData.GcCpuEntry;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Collects per-collection CPU times from {@code jdk.GCCPUTime} events, most recent collection
 * first, capped to keep the payload bounded. A missing time component (e.g. {@code realTime} can
 * be null) is reported as 0.
 */
public class GcCpuTimesBuilder implements RecordBuilder<GenericRecord, List<GcCpuEntry>> {

    private static final String GC_ID_FIELD = "gcId";
    private static final String USER_TIME_FIELD = "userTime";
    private static final String SYSTEM_TIME_FIELD = "systemTime";
    private static final String REAL_TIME_FIELD = "realTime";

    private final int maxEntries;
    private final List<GcCpuEntry> entries = new ArrayList<>();

    public GcCpuTimesBuilder(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive: " + maxEntries);
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long gcId = Json.readLong(fields, GC_ID_FIELD);
        if (gcId < 0) {
            return;
        }
        entries.add(new GcCpuEntry(
                gcId,
                Math.max(0, Json.readLong(fields, USER_TIME_FIELD)),
                Math.max(0, Json.readLong(fields, SYSTEM_TIME_FIELD)),
                Math.max(0, Json.readLong(fields, REAL_TIME_FIELD))));
    }

    @Override
    public List<GcCpuEntry> build() {
        entries.sort(Comparator.comparingLong(GcCpuEntry::gcId).reversed());
        return entries.size() > maxEntries ? List.copyOf(entries.subList(0, maxEntries)) : entries;
    }
}
