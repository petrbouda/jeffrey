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

package cafe.jeffrey.microscope.profile.management.manager.model.nmt;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.microscope.profile.management.timeseries.MaxPerSecondSeries;
import cafe.jeffrey.microscope.profile.persistence.api.GenericRecord;
import cafe.jeffrey.microscope.profile.persistence.api.RecordBuilder;
import cafe.jeffrey.microscope.common.Json;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.microscope.profile.management.timeseries.SingleSerie;
import cafe.jeffrey.microscope.profile.management.timeseries.TimeseriesData;

/**
 * Builds the total reserved-vs-committed native-memory timeline from {@code jdk.NativeMemoryUsageTotal}.
 * Both are gauges: each second keeps the maximum sampled value, gaps carry the previous value forward.
 */
public class NmtTotalTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String COMMITTED_SERIES_NAME = "Committed";
    private static final String RESERVED_SERIES_NAME = "Reserved";
    private static final String RESERVED_FIELD = "reserved";
    private static final String COMMITTED_FIELD = "committed";

    private final MaxPerSecondSeries committedTimeseries;
    private final MaxPerSecondSeries reservedTimeseries;

    public NmtTotalTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.committedTimeseries = new MaxPerSecondSeries(COMMITTED_SERIES_NAME, timeRange);
        this.reservedTimeseries = new MaxPerSecondSeries(RESERVED_SERIES_NAME, timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long seconds = record.timestampFromStart().toSeconds();
        long committed = Json.readLong(fields, COMMITTED_FIELD);
        if (committed >= 0) {
            committedTimeseries.record(seconds, committed);
        }
        long reserved = Json.readLong(fields, RESERVED_FIELD);
        if (reserved >= 0) {
            reservedTimeseries.record(seconds, reserved);
        }
    }

    @Override
    public TimeseriesData build() {
        SingleSerie committedSerie = committedTimeseries.buildCarryingForward();
        SingleSerie reservedSerie = reservedTimeseries.buildCarryingForward();
        return new TimeseriesData(committedSerie, reservedSerie);
    }
}
