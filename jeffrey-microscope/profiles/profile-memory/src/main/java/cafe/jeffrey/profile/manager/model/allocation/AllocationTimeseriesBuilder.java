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

package cafe.jeffrey.profile.manager.model.allocation;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

/**
 * Builds an allocated-bytes-per-second series from the allocation events (bytes = event weight).
 */
public class AllocationTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String SERIES_NAME = "Allocated Bytes / sec";

    private final LongLongHashMap allocationTimeseries;

    public AllocationTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.allocationTimeseries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        long bytes = Math.max(0, record.sampleWeight());
        if (bytes == 0) {
            return;
        }
        long seconds = record.timestampFromStart().toSeconds();
        allocationTimeseries.addToValue(seconds, bytes);
    }

    @Override
    public TimeseriesData build() {
        return new TimeseriesData(TimeseriesUtils.buildSerie(SERIES_NAME, allocationTimeseries));
    }
}
