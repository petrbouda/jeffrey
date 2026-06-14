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
import cafe.jeffrey.profile.manager.model.gc.tables.StringSymbolTablesData.Deduplication;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

import tools.jackson.databind.node.ObjectNode;

/**
 * Aggregates {@code jdk.StringDeduplication} events (one per dedup cycle) into grand totals plus a
 * per-second activity timeline (deduplicated strings and heap bytes saved). String deduplication is
 * a G1/Shenandoah feature that is off by default ({@code -XX:+UseStringDeduplication}); when it is
 * disabled there are no events and the result is all-zero.
 */
public class StringDeduplicationBuilder implements RecordBuilder<GenericRecord, Deduplication> {

    private static final String INSPECTED_FIELD = "inspected";
    private static final String DEDUPLICATED_FIELD = "deduplicated";
    private static final String NEW_STRINGS_FIELD = "newStrings";
    private static final String DEDUPLICATED_SIZE_FIELD = "deduplicatedSize";
    private static final String DEDUPLICATED_SERIES = "Deduplicated";
    private static final String BYTES_SAVED_SERIES = "Bytes Saved";

    private final LongLongHashMap deduplicatedSeries;
    private final LongLongHashMap bytesSavedSeries;
    private long cycles;
    private long totalInspected;
    private long totalDeduplicated;
    private long totalNewStrings;
    private long totalBytesSaved;

    public StringDeduplicationBuilder(RelativeTimeRange timeRange) {
        this.deduplicatedSeries = TimeseriesUtils.initWithZeros(timeRange);
        this.bytesSavedSeries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long seconds = record.timestampFromStart().toSeconds();
        long inspected = Math.max(0, Json.readLong(fields, INSPECTED_FIELD));
        long deduplicated = Math.max(0, Json.readLong(fields, DEDUPLICATED_FIELD));
        long newStrings = Math.max(0, Json.readLong(fields, NEW_STRINGS_FIELD));
        long bytesSaved = Math.max(0, Json.readLong(fields, DEDUPLICATED_SIZE_FIELD));

        cycles++;
        totalInspected += inspected;
        totalDeduplicated += deduplicated;
        totalNewStrings += newStrings;
        totalBytesSaved += bytesSaved;

        deduplicatedSeries.addToValue(seconds, deduplicated);
        bytesSavedSeries.addToValue(seconds, bytesSaved);
    }

    @Override
    public Deduplication build() {
        SingleSerie deduplicatedSerie = TimeseriesUtils.buildSerie(DEDUPLICATED_SERIES, deduplicatedSeries);
        SingleSerie bytesSavedSerie = TimeseriesUtils.buildSerie(BYTES_SAVED_SERIES, bytesSavedSeries);

        return new Deduplication(
                cycles,
                totalInspected,
                totalDeduplicated,
                totalNewStrings,
                totalBytesSaved,
                new TimeseriesData(deduplicatedSerie, bytesSavedSerie));
    }
}
