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
