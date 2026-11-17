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

package cafe.jeffrey.profile.manager.model.gc;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.Type;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds per-evacuation {@link G1PlabStatistics} rows from {@code jdk.G1EvacuationYoungStatistics} and
 * {@code jdk.G1EvacuationOldStatistics}. The nested {@code statistics} struct is flattened to dotted JSON
 * keys ({@code statistics.allocated}, …) by the event-to-JSON mapper. Rows are ordered by gcId, then
 * generation (Young before Old).
 */
public class G1PlabStatisticsBuilder implements RecordBuilder<GenericRecord, List<G1PlabStatistics>> {

    private static final String STATS_PREFIX = "statistics.";
    private static final String GC_ID_FIELD = STATS_PREFIX + "gcId";
    private static final String ALLOCATED_FIELD = STATS_PREFIX + "allocated";
    private static final String USED_FIELD = STATS_PREFIX + "used";
    private static final String WASTED_FIELD = STATS_PREFIX + "wasted";
    private static final String UNDO_WASTE_FIELD = STATS_PREFIX + "undoWaste";
    private static final String REGION_END_WASTE_FIELD = STATS_PREFIX + "regionEndWaste";
    private static final String DIRECT_ALLOCATED_FIELD = STATS_PREFIX + "directAllocated";
    private static final String REGIONS_REFILLED_FIELD = STATS_PREFIX + "regionsRefilled";
    private static final String NUM_PLABS_FILLED_FIELD = STATS_PREFIX + "numPlabsFilled";
    private static final String FAILURE_USED_FIELD = STATS_PREFIX + "failureUsed";
    private static final String FAILURE_WASTE_FIELD = STATS_PREFIX + "failureWaste";

    private final List<G1PlabStatistics> rows = new ArrayList<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();

        String generation = Type.G1_EVACUATION_YOUNG_STATISTICS.equals(record.type())
                ? G1PlabStatistics.YOUNG
                : G1PlabStatistics.OLD;

        long allocated = Math.max(0, Json.readLong(fields, ALLOCATED_FIELD));
        long failureWaste = Math.max(0, Json.readLong(fields, FAILURE_WASTE_FIELD));
        long totalWasted = Math.max(0, Json.readLong(fields, WASTED_FIELD))
                + Math.max(0, Json.readLong(fields, UNDO_WASTE_FIELD))
                + Math.max(0, Json.readLong(fields, REGION_END_WASTE_FIELD))
                + failureWaste;
        double wastePercent = allocated > 0 ? (totalWasted * 100.0) / allocated : 0;

        rows.add(new G1PlabStatistics(
                Json.readLong(fields, GC_ID_FIELD),
                generation,
                allocated,
                Math.max(0, Json.readLong(fields, USED_FIELD)),
                totalWasted,
                wastePercent,
                Math.max(0, Json.readLong(fields, DIRECT_ALLOCATED_FIELD)),
                Math.max(0, Json.readLong(fields, REGIONS_REFILLED_FIELD)),
                Math.max(0, Json.readLong(fields, NUM_PLABS_FILLED_FIELD)),
                Math.max(0, Json.readLong(fields, FAILURE_USED_FIELD)),
                failureWaste));
    }

    @Override
    public List<G1PlabStatistics> build() {
        rows.sort(Comparator.comparingLong(G1PlabStatistics::gcId)
                .thenComparingInt(row -> G1PlabStatistics.YOUNG.equals(row.generation()) ? 0 : 1));
        return rows;
    }
}
