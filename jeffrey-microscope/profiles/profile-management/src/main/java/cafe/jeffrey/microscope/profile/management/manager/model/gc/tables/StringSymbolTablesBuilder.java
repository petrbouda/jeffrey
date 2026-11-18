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

package cafe.jeffrey.microscope.profile.management.manager.model.gc.tables;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.microscope.profile.management.timeseries.MaxPerSecondSeries;
import cafe.jeffrey.microscope.profile.management.manager.model.gc.tables.StringSymbolTablesData.Deduplication;
import cafe.jeffrey.microscope.profile.management.manager.model.gc.tables.StringSymbolTablesData.Header;
import cafe.jeffrey.microscope.profile.persistence.api.GenericRecord;
import cafe.jeffrey.microscope.profile.persistence.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.microscope.profile.management.timeseries.TimeseriesData;

/**
 * Builds entry-count and footprint timelines for the String and Symbol intern tables from the
 * periodic {@code jdk.StringTableStatistics} / {@code jdk.SymbolTableStatistics} gauges. Each
 * second carries the max sample (and forward-fills empty seconds), matching {@code IhopTimeseriesBuilder}.
 */
public class StringSymbolTablesBuilder implements RecordBuilder<GenericRecord, StringSymbolTablesData> {

    private static final String ENTRY_COUNT_FIELD = "entryCount";
    private static final String TOTAL_FOOTPRINT_FIELD = "totalFootprint";
    private static final String STRING_TABLE_SERIES = "String Table";
    private static final String SYMBOL_TABLE_SERIES = "Symbol Table";

    private final MaxPerSecondSeries stringEntries;
    private final MaxPerSecondSeries symbolEntries;
    private final MaxPerSecondSeries stringFootprint;
    private final MaxPerSecondSeries symbolFootprint;
    private long peakStringEntries;
    private long peakStringFootprint;
    private long peakSymbolEntries;
    private long peakSymbolFootprint;

    public StringSymbolTablesBuilder(RelativeTimeRange timeRange) {
        this.stringEntries = new MaxPerSecondSeries(STRING_TABLE_SERIES, timeRange);
        this.symbolEntries = new MaxPerSecondSeries(SYMBOL_TABLE_SERIES, timeRange);
        this.stringFootprint = new MaxPerSecondSeries(STRING_TABLE_SERIES, timeRange);
        this.symbolFootprint = new MaxPerSecondSeries(SYMBOL_TABLE_SERIES, timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long seconds = record.timestampFromStart().toSeconds();
        long entries = Math.max(0, Json.readLong(fields, ENTRY_COUNT_FIELD));
        long footprint = Math.max(0, Json.readLong(fields, TOTAL_FOOTPRINT_FIELD));

        if (EventTypeName.STRING_TABLE_STATISTICS.equals(record.type().code())) {
            stringEntries.record(seconds, entries);
            stringFootprint.record(seconds, footprint);
            peakStringEntries = Math.max(peakStringEntries, entries);
            peakStringFootprint = Math.max(peakStringFootprint, footprint);
        } else if (EventTypeName.SYMBOL_TABLE_STATISTICS.equals(record.type().code())) {
            symbolEntries.record(seconds, entries);
            symbolFootprint.record(seconds, footprint);
            peakSymbolEntries = Math.max(peakSymbolEntries, entries);
            peakSymbolFootprint = Math.max(peakSymbolFootprint, footprint);
        }
    }

    @Override
    public StringSymbolTablesData build() {
        return new StringSymbolTablesData(
                new Header(peakStringEntries, peakStringFootprint, peakSymbolEntries, peakSymbolFootprint),
                new TimeseriesData(stringEntries.buildCarryingForward(), symbolEntries.buildCarryingForward()),
                new TimeseriesData(stringFootprint.buildCarryingForward(), symbolFootprint.buildCarryingForward()),
                Deduplication.empty());
    }
}
