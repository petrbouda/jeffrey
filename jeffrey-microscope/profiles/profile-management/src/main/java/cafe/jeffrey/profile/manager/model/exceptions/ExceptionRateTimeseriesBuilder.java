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

package cafe.jeffrey.profile.manager.model.exceptions;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

/**
 * Builds an exceptions-per-second timeline from periodic {@code jdk.ExceptionStatistics} events.
 * The event carries a cumulative {@code throwables} gauge, so each sample contributes the delta
 * against the previous sample. Requires a time-ordered stream; the first sample establishes the
 * baseline and contributes nothing. Negative deltas (JVM restart inside one recording) are clamped
 * to zero.
 */
public class ExceptionRateTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String SERIES_NAME = "Exceptions / sec";
    private static final String THROWABLES_FIELD = "throwables";

    private final LongLongHashMap rateTimeseries;

    private long previousCumulative = -1;

    public ExceptionRateTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.rateTimeseries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        long cumulative = Json.readLong(record.jsonFields(), THROWABLES_FIELD);
        if (cumulative < 0) {
            return;
        }

        if (previousCumulative >= 0) {
            long delta = Math.max(0, cumulative - previousCumulative);
            long seconds = record.timestampFromStart().toSeconds();
            rateTimeseries.addToValue(seconds, delta);
        }
        previousCumulative = cumulative;
    }

    @Override
    public TimeseriesData build() {
        SingleSerie serie = TimeseriesUtils.buildSerie(SERIES_NAME, rateTimeseries);
        return new TimeseriesData(serie);
    }
}
