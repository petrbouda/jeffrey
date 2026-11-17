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

package cafe.jeffrey.profile.manager.model.gc.tuning;

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
 * Builds the G1 IHOP timeline from {@code jdk.G1AdaptiveIHOP} events: the marking-start threshold
 * vs the current old-generation occupancy, in bytes. When the occupancy line crosses the threshold
 * line, G1 starts a concurrent marking cycle — this chart explains when and why cycles begin.
 * Both series carry the last sampled value across seconds without an event.
 */
public class IhopTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String THRESHOLD_SERIES_NAME = "IHOP Threshold";
    private static final String OCCUPANCY_SERIES_NAME = "Old Gen Occupancy";
    private static final String THRESHOLD_FIELD = "threshold";
    private static final String CURRENT_OCCUPANCY_FIELD = "currentOccupancy";
    private static final long CARRY_FORWARD_MARK = 0L;

    private final LongLongHashMap thresholdTimeseries;
    private final LongLongHashMap occupancyTimeseries;

    public IhopTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.thresholdTimeseries = TimeseriesUtils.initWithZeros(timeRange);
        this.occupancyTimeseries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long seconds = record.timestampFromStart().toSeconds();

        long threshold = Json.readLong(fields, THRESHOLD_FIELD);
        if (threshold >= 0) {
            thresholdTimeseries.updateValue(seconds, 0, existing -> Math.max(existing, threshold));
        }
        long occupancy = Json.readLong(fields, CURRENT_OCCUPANCY_FIELD);
        if (occupancy >= 0) {
            occupancyTimeseries.updateValue(seconds, 0, existing -> Math.max(existing, occupancy));
        }
    }

    @Override
    public TimeseriesData build() {
        SingleSerie thresholdSerie = TimeseriesUtils.buildSerie(THRESHOLD_SERIES_NAME, thresholdTimeseries);
        SingleSerie occupancySerie = TimeseriesUtils.buildSerie(OCCUPANCY_SERIES_NAME, occupancyTimeseries);
        TimeseriesUtils.remapTimeseriesBySteps(thresholdSerie, CARRY_FORWARD_MARK);
        TimeseriesUtils.remapTimeseriesBySteps(occupancySerie, CARRY_FORWARD_MARK);
        return new TimeseriesData(thresholdSerie, occupancySerie);
    }
}
