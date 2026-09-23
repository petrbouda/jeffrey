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

package cafe.jeffrey.profile.manager.model.system;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

/**
 * Builds the OS swap-space timeline from periodic {@code jdk.SwapSpace} events: total swap and used swap
 * ({@code totalSize - freeSize}) in bytes, so the UI can spot a host that starts paging.
 */
public class SwapTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String TOTAL_SERIES_NAME = "Total";
    private static final String USED_SERIES_NAME = "Used";
    private static final String TOTAL_SIZE_FIELD = "totalSize";
    private static final String FREE_SIZE_FIELD = "freeSize";

    private final LongLongHashMap totalTimeseries;
    private final LongLongHashMap usedTimeseries;

    public SwapTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.totalTimeseries = TimeseriesUtils.initWithZeros(timeRange);
        this.usedTimeseries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long seconds = record.timestampFromStart().toSeconds();

        long total = Math.max(0, Json.readLong(fields, TOTAL_SIZE_FIELD));
        long free = Math.max(0, Json.readLong(fields, FREE_SIZE_FIELD));
        long used = Math.max(0, total - free);

        totalTimeseries.updateValue(seconds, 0, existing -> Math.max(existing, total));
        usedTimeseries.updateValue(seconds, 0, existing -> Math.max(existing, used));
    }

    @Override
    public TimeseriesData build() {
        SingleSerie totalSerie = TimeseriesUtils.buildSerie(TOTAL_SERIES_NAME, totalTimeseries);
        SingleSerie usedSerie = TimeseriesUtils.buildSerie(USED_SERIES_NAME, usedTimeseries);
        return new TimeseriesData(totalSerie, usedSerie);
    }
}
