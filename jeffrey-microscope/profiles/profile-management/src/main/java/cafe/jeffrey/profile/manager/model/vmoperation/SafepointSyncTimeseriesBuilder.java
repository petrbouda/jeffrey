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

package cafe.jeffrey.profile.manager.model.vmoperation;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

import java.time.Duration;
import java.util.List;

/**
 * Sums time-to-safepoint per second from {@code jdk.SafepointStateSynchronization} — the time the
 * JVM spends waiting for all threads to reach a safepoint, distinct from the safepoint operation
 * itself — building a single "Time to Safepoint" series.
 */
public class SafepointSyncTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String SERIES_NAME = "Time to Safepoint";

    private final LongLongHashMap values;

    public SafepointSyncTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.values = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        Duration duration = record.duration();
        if (duration == null) {
            return;
        }
        values.addToValue(record.timestampFromStart().toSeconds(), duration.toNanos());
    }

    @Override
    public TimeseriesData build() {
        return new TimeseriesData(List.of(TimeseriesUtils.buildSerie(SERIES_NAME, values)));
    }
}
