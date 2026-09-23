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

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

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
    private static final long CARRY_FORWARD_MARK = 0L;

    private final LongLongHashMap memoryUsedTimeseries;
    private final LongLongHashMap countTimeseries;

    public DirectBufferTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.memoryUsedTimeseries = TimeseriesUtils.initWithZeros(timeRange);
        this.countTimeseries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        long seconds = record.timestampFromStart().toSeconds();
        long memoryUsed = Json.readLong(record.jsonFields(), MEMORY_USED_FIELD);
        if (memoryUsed >= 0) {
            memoryUsedTimeseries.updateValue(seconds, 0, existing -> Math.max(existing, memoryUsed));
        }
        long count = Json.readLong(record.jsonFields(), COUNT_FIELD);
        if (count >= 0) {
            countTimeseries.updateValue(seconds, 0, existing -> Math.max(existing, count));
        }
    }

    @Override
    public TimeseriesData build() {
        SingleSerie memorySerie = TimeseriesUtils.buildSerie(MEMORY_USED_SERIES_NAME, memoryUsedTimeseries);
        SingleSerie countSerie = TimeseriesUtils.buildSerie(COUNT_SERIES_NAME, countTimeseries);
        TimeseriesUtils.remapTimeseriesBySteps(memorySerie, CARRY_FORWARD_MARK);
        TimeseriesUtils.remapTimeseriesBySteps(countSerie, CARRY_FORWARD_MARK);
        return new TimeseriesData(memorySerie, countSerie);
    }
}
