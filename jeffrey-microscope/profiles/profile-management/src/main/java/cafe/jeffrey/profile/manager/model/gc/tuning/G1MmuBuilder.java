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
import cafe.jeffrey.profile.manager.model.gc.tuning.IhopData.MmuEntry;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Collects {@code jdk.G1MMU} samples (GC time vs pause target per collection), most recent
 * collection first, capped to keep the payload bounded.
 */
public class G1MmuBuilder implements RecordBuilder<GenericRecord, List<MmuEntry>> {

    private static final String GC_ID_FIELD = "gcId";
    private static final String GC_TIME_FIELD = "gcTime";
    private static final String PAUSE_TARGET_FIELD = "pauseTarget";

    private final int maxEntries;
    private final List<MmuEntry> entries = new ArrayList<>();

    public G1MmuBuilder(int maxEntries) {
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
        entries.add(new MmuEntry(
                gcId,
                Math.max(0, Json.readLong(fields, GC_TIME_FIELD)),
                Math.max(0, Json.readLong(fields, PAUSE_TARGET_FIELD)),
                record.timestampFromStart().toMillis()));
    }

    @Override
    public List<MmuEntry> build() {
        entries.sort(Comparator.comparingLong(MmuEntry::gcId).reversed());
        return entries.size() > maxEntries ? List.copyOf(entries.subList(0, maxEntries)) : entries;
    }
}
