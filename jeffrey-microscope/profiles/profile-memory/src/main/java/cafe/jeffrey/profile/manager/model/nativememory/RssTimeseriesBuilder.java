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
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
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
