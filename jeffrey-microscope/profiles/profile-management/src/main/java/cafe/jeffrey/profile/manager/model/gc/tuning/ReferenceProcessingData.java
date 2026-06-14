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
