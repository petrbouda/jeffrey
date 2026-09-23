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

package cafe.jeffrey.profile.manager.model.nmt;

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
 * Builds the total reserved-vs-committed native-memory timeline from {@code jdk.NativeMemoryUsageTotal}.
 * Both are gauges: each second keeps the maximum sampled value, gaps carry the previous value forward.
 */
public class NmtTotalTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String COMMITTED_SERIES_NAME = "Committed";
    private static final String RESERVED_SERIES_NAME = "Reserved";
    private static final String RESERVED_FIELD = "reserved";
    private static final String COMMITTED_FIELD = "committed";
    private static final long CARRY_FORWARD_MARK = 0L;

    private final LongLongHashMap committedTimeseries;
    private final LongLongHashMap reservedTimeseries;

    public NmtTotalTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.committedTimeseries = TimeseriesUtils.initWithZeros(timeRange);
        this.reservedTimeseries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long seconds = record.timestampFromStart().toSeconds();
        long committed = Json.readLong(fields, COMMITTED_FIELD);
        if (committed >= 0) {
            committedTimeseries.updateValue(seconds, 0, existing -> Math.max(existing, committed));
        }
        long reserved = Json.readLong(fields, RESERVED_FIELD);
        if (reserved >= 0) {
            reservedTimeseries.updateValue(seconds, 0, existing -> Math.max(existing, reserved));
        }
    }

    @Override
    public TimeseriesData build() {
        SingleSerie committedSerie = TimeseriesUtils.buildSerie(COMMITTED_SERIES_NAME, committedTimeseries);
        SingleSerie reservedSerie = TimeseriesUtils.buildSerie(RESERVED_SERIES_NAME, reservedTimeseries);
        TimeseriesUtils.remapTimeseriesBySteps(committedSerie, CARRY_FORWARD_MARK);
        TimeseriesUtils.remapTimeseriesBySteps(reservedSerie, CARRY_FORWARD_MARK);
        return new TimeseriesData(committedSerie, reservedSerie);
    }
}
