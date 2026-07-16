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
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

/**
 * Builds the RSS-vs-heap timeline from a mixed stream of {@code jdk.ResidentSetSize} (process
 * resident set size) and {@code jdk.GCHeapSummary} ({@code heapUsed}) events. Both are gauges:
 * each second keeps the maximum sampled value, and seconds without a sample carry the previous
 * value forward. A widening gap between the two series is native (off-heap) memory growth.
 */
public class RssTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String RSS_SERIES_NAME = "Resident Set Size";
    private static final String HEAP_USED_SERIES_NAME = "Heap Used";
    private static final String SIZE_FIELD = "size";
    private static final String HEAP_USED_FIELD = "heapUsed";
    private static final long CARRY_FORWARD_MARK = 0L;

    private final LongLongHashMap rssTimeseries;
    private final LongLongHashMap heapUsedTimeseries;

    public RssTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.rssTimeseries = TimeseriesUtils.initWithZeros(timeRange);
        this.heapUsedTimeseries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        long seconds = record.timestampFromStart().toSeconds();
        if (Type.RESIDENT_SET_SIZE.equals(record.type())) {
            long size = Json.readLong(record.jsonFields(), SIZE_FIELD);
            if (size >= 0) {
                rssTimeseries.updateValue(seconds, 0, existing -> Math.max(existing, size));
            }
        } else {
            long heapUsed = Json.readLong(record.jsonFields(), HEAP_USED_FIELD);
            if (heapUsed >= 0) {
                heapUsedTimeseries.updateValue(seconds, 0, existing -> Math.max(existing, heapUsed));
            }
        }
    }

    @Override
    public TimeseriesData build() {
        SingleSerie rssSerie = TimeseriesUtils.buildSerie(RSS_SERIES_NAME, rssTimeseries);
        SingleSerie heapSerie = TimeseriesUtils.buildSerie(HEAP_USED_SERIES_NAME, heapUsedTimeseries);
        TimeseriesUtils.remapTimeseriesBySteps(rssSerie, CARRY_FORWARD_MARK);
        TimeseriesUtils.remapTimeseriesBySteps(heapSerie, CARRY_FORWARD_MARK);
        return new TimeseriesData(rssSerie, heapSerie);
    }
}
