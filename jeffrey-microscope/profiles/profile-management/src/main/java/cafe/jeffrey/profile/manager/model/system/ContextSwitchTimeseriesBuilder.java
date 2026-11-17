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

package cafe.jeffrey.profile.manager.model.system;

import cafe.jeffrey.timeseries.MaxPerSecondSeries;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.TimeseriesData;

/**
 * Builds the thread context-switch rate timeline from {@code jdk.ThreadContextSwitchRate} events.
 * The {@code switchRate} field is float-typed and arrives as a numeric string, hence
 * {@link Json#readDouble}; values are rounded to whole switches per second.
 */
public class ContextSwitchTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String SERIES_NAME = "Context Switches / sec";
    private static final String SWITCH_RATE_FIELD = "switchRate";

    private final MaxPerSecondSeries rateTimeseries;

    public ContextSwitchTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.rateTimeseries = new MaxPerSecondSeries(SERIES_NAME, timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        double rate = Json.readDouble(record.jsonFields(), SWITCH_RATE_FIELD);
        if (rate < 0) {
            return;
        }
        long seconds = record.timestampFromStart().toSeconds();
        long roundedRate = Math.round(rate);
        rateTimeseries.record(seconds, roundedRate);
    }

    @Override
    public TimeseriesData build() {
        return new TimeseriesData(rateTimeseries.build());
    }
}
