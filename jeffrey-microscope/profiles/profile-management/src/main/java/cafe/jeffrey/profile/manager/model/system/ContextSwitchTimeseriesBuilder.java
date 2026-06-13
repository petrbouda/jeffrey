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

package cafe.jeffrey.profile.manager.model.system;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
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
