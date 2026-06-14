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

package cafe.jeffrey.profile.manager.model.gc.tables;

import cafe.jeffrey.timeseries.TimeseriesData;

/**
 * JVM intern-table footprint insight from {@code jdk.StringTableStatistics} and
 * {@code jdk.SymbolTableStatistics} (periodic gauges).
 *
 * @param header        peak entry counts and footprints for both tables
 * @param entries       entry-count over time, series "String Table" and "Symbol Table"
 * @param footprint     table footprint (bytes) over time, series "String Table" and "Symbol Table"
 * @param deduplication String-deduplication activity ({@code jdk.StringDeduplication}); zeroed when off
 */
public record StringSymbolTablesData(
        Header header,
        TimeseriesData entries,
        TimeseriesData footprint,
        Deduplication deduplication) {

    public StringSymbolTablesData withDeduplication(Deduplication deduplication) {
        return new StringSymbolTablesData(header, entries, footprint, deduplication);
    }

    public record Header(
            long peakStringEntries,
            long peakStringFootprint,
            long peakSymbolEntries,
            long peakSymbolFootprint) {
    }

    /**
     * String-deduplication summary from {@code jdk.StringDeduplication} (each event is one dedup cycle).
     *
     * @param cycles            number of deduplication cycles recorded
     * @param totalInspected    total strings inspected across all cycles
     * @param totalDeduplicated total strings deduplicated across all cycles
     * @param totalNewStrings   total new (first-seen) strings across all cycles
     * @param totalBytesSaved   total heap saved in bytes ({@code deduplicatedSize})
     * @param timeline          per-second deduplicated count ("Deduplicated") and bytes saved ("Bytes Saved")
     */
    public record Deduplication(
            long cycles,
            long totalInspected,
            long totalDeduplicated,
            long totalNewStrings,
            long totalBytesSaved,
            TimeseriesData timeline) {

        public static Deduplication empty() {
            return new Deduplication(0, 0, 0, 0, 0, TimeseriesData.empty());
        }
    }
}
