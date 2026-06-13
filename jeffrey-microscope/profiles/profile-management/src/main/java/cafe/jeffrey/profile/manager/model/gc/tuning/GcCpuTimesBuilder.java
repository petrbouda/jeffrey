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
