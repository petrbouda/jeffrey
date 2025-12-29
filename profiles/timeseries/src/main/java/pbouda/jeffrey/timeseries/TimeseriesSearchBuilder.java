/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.timeseries;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import pbouda.jeffrey.shared.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.api.builder.RecordBuilder;
import pbouda.jeffrey.provider.api.repository.model.TimeseriesSearchRecord;

public class TimeseriesSearchBuilder implements RecordBuilder<TimeseriesSearchRecord, TimeseriesData> {

    private final LongLongHashMap values;
    private final LongLongHashMap matchedValues;

    public TimeseriesSearchBuilder(RelativeTimeRange timeRange) {
        this.values = TimeseriesUtils.initWithZeros(timeRange);
        this.matchedValues = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(TimeseriesSearchRecord record) {
        matchedValues.addToValue(record.second(), record.matched());
        values.addToValue(record.second(), record.total() - record.matched());
    }

    @Override
    public TimeseriesData build() {
        return new TimeseriesData(
                TimeseriesUtils.buildSerie("Samples", values),
                TimeseriesUtils.buildSerie("Matched Samples", matchedValues));
    }
}
