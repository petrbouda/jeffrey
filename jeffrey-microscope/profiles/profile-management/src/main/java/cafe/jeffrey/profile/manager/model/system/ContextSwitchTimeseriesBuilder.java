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

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

/**
 * Builds the thread context-switch rate timeline from {@code jdk.ThreadContextSwitchRate} events.
 * The {@code switchRate} field is float-typed and arrives as a numeric string, hence
 * {@link Json#readDouble}; values are rounded to whole switches per second.
 */
public class ContextSwitchTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String SERIES_NAME = "Context Switches / sec";
    private static final String SWITCH_RATE_FIELD = "switchRate";

    private final LongLongHashMap rateTimeseries;

    public ContextSwitchTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.rateTimeseries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        double rate = Json.readDouble(record.jsonFields(), SWITCH_RATE_FIELD);
        if (rate < 0) {
            return;
        }
        long seconds = record.timestampFromStart().toSeconds();
        long roundedRate = Math.round(rate);
        rateTimeseries.updateValue(seconds, 0, existing -> Math.max(existing, roundedRate));
    }

    @Override
    public TimeseriesData build() {
        return new TimeseriesData(TimeseriesUtils.buildSerie(SERIES_NAME, rateTimeseries));
    }
}
