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

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.manager.model.gc.tables.StringSymbolTablesData.Deduplication;
import cafe.jeffrey.profile.manager.model.gc.tables.StringSymbolTablesData.Header;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

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
    private static final long CARRY_FORWARD_MARK = 0L;

    private final LongLongHashMap stringEntries;
    private final LongLongHashMap symbolEntries;
    private final LongLongHashMap stringFootprint;
    private final LongLongHashMap symbolFootprint;
    private long peakStringEntries;
    private long peakStringFootprint;
    private long peakSymbolEntries;
    private long peakSymbolFootprint;

    public StringSymbolTablesBuilder(RelativeTimeRange timeRange) {
        this.stringEntries = TimeseriesUtils.initWithZeros(timeRange);
        this.symbolEntries = TimeseriesUtils.initWithZeros(timeRange);
        this.stringFootprint = TimeseriesUtils.initWithZeros(timeRange);
        this.symbolFootprint = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long seconds = record.timestampFromStart().toSeconds();
        long entries = Math.max(0, Json.readLong(fields, ENTRY_COUNT_FIELD));
        long footprint = Math.max(0, Json.readLong(fields, TOTAL_FOOTPRINT_FIELD));

        if (EventTypeName.STRING_TABLE_STATISTICS.equals(record.type().code())) {
            accumulateMax(stringEntries, seconds, entries);
            accumulateMax(stringFootprint, seconds, footprint);
            peakStringEntries = Math.max(peakStringEntries, entries);
            peakStringFootprint = Math.max(peakStringFootprint, footprint);
        } else if (EventTypeName.SYMBOL_TABLE_STATISTICS.equals(record.type().code())) {
            accumulateMax(symbolEntries, seconds, entries);
            accumulateMax(symbolFootprint, seconds, footprint);
            peakSymbolEntries = Math.max(peakSymbolEntries, entries);
            peakSymbolFootprint = Math.max(peakSymbolFootprint, footprint);
        }
    }

    private static void accumulateMax(LongLongHashMap series, long seconds, long value) {
        series.updateValue(seconds, 0, existing -> Math.max(existing, value));
    }

    @Override
    public StringSymbolTablesData build() {
        return new StringSymbolTablesData(
                new Header(peakStringEntries, peakStringFootprint, peakSymbolEntries, peakSymbolFootprint),
                new TimeseriesData(carried(STRING_TABLE_SERIES, stringEntries), carried(SYMBOL_TABLE_SERIES, symbolEntries)),
                new TimeseriesData(carried(STRING_TABLE_SERIES, stringFootprint), carried(SYMBOL_TABLE_SERIES, symbolFootprint)),
                Deduplication.empty());
    }

    private static SingleSerie carried(String name, LongLongHashMap values) {
        SingleSerie serie = TimeseriesUtils.buildSerie(name, values);
        TimeseriesUtils.remapTimeseriesBySteps(serie, CARRY_FORWARD_MARK);
        return serie;
    }
}
