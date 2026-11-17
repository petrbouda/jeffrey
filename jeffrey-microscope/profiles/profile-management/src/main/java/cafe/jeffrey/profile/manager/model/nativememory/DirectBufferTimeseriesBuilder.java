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

package cafe.jeffrey.profile.manager.model.nativememory;

import cafe.jeffrey.timeseries.MaxPerSecondSeries;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;

/**
 * Builds the direct (off-heap NIO) buffer timeline from periodic {@code jdk.DirectBufferStatistics}
 * events: memory used (bytes) plus buffer count. Both are gauges with carry-forward across seconds
 * without a sample. Steady growth here is the classic NIO/Netty buffer-leak pattern.
 */
public class DirectBufferTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String MEMORY_USED_SERIES_NAME = "Direct Buffer Memory";
    private static final String COUNT_SERIES_NAME = "Buffer Count";
    private static final String MEMORY_USED_FIELD = "memoryUsed";
    private static final String COUNT_FIELD = "count";

    private final MaxPerSecondSeries memoryUsedTimeseries;
    private final MaxPerSecondSeries countTimeseries;

    public DirectBufferTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.memoryUsedTimeseries = new MaxPerSecondSeries(MEMORY_USED_SERIES_NAME, timeRange);
        this.countTimeseries = new MaxPerSecondSeries(COUNT_SERIES_NAME, timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        long seconds = record.timestampFromStart().toSeconds();
        long memoryUsed = Json.readLong(record.jsonFields(), MEMORY_USED_FIELD);
        if (memoryUsed >= 0) {
            memoryUsedTimeseries.record(seconds, memoryUsed);
        }
        long count = Json.readLong(record.jsonFields(), COUNT_FIELD);
        if (count >= 0) {
            countTimeseries.record(seconds, count);
        }
    }

    @Override
    public TimeseriesData build() {
        SingleSerie memorySerie = memoryUsedTimeseries.buildCarryingForward();
        SingleSerie countSerie = countTimeseries.buildCarryingForward();
        return new TimeseriesData(memorySerie, countSerie);
    }
}
