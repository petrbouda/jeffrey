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

package cafe.jeffrey.profile.manager.model.nativememory;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
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
