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

import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.Map;

/**
 * Reference-processing insight from {@code jdk.GCReferenceStatistics} — the count of Soft/Weak/Final/
 * Phantom (and Cleaner/Other) references the GC processed. On JDK 26 the event carries only
 * {@code gcId}, {@code type} and {@code count} (no processing time), so every view is count-based:
 * high Soft counts hint at memory pressure clearing soft references; high Final/Phantom counts hint at
 * finalizer/cleaner backlog.
 *
 * @param header   headline counters
 * @param byType   per-reference-type totals + average per GC, ordered by descending total
 * @param timeline references processed per second, one series per reference type (stacked)
 * @param perGc    per-collection breakdown, ordered by descending total references, capped
 */
public record ReferenceProcessingData(
        Header header,
        List<ReferenceTypeStat> byType,
        TimeseriesData timeline,
        List<GcReferenceBreakdown> perGc) {

    /**
     * @param totalReferences total references processed across the recording
     * @param distinctTypes   number of distinct reference types observed
     * @param gcCount         number of GC cycles that processed references
     * @param dominantType    the reference type with the highest total ({@code null} when none)
     */
    public record Header(
            long totalReferences,
            int distinctTypes,
            long gcCount,
            String dominantType) {
    }

    public record ReferenceTypeStat(String type, long total, long avgPerGc) {
    }

    /**
     * Per-collection reference totals.
     *
     * @param gcId          collection identifier
     * @param total         total references processed in this collection
     * @param countsByType  references processed per type within this collection
     */
    public record GcReferenceBreakdown(long gcId, long total, Map<String, Long> countsByType) {
    }
}
