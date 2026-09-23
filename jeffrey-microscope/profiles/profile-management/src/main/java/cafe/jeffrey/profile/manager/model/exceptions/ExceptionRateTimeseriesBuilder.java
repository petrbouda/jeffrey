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

package cafe.jeffrey.profile.manager.model.exceptions;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
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
