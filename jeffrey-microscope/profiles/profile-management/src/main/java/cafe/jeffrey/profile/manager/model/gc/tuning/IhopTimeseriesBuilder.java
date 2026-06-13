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

package cafe.jeffrey.profile.manager.model.gc.tuning;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
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
