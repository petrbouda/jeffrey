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

package cafe.jeffrey.profile.manager.model.allocation;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
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
