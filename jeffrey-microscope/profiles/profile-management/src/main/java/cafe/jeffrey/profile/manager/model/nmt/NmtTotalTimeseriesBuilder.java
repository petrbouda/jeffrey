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

package cafe.jeffrey.profile.manager.model.nmt;

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
