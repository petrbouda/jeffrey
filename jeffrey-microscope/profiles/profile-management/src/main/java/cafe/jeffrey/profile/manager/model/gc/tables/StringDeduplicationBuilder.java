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

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.profile.manager.model.gc.tables.StringSymbolTablesData.Deduplication;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
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
