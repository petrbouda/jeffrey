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
import pbouda.jeffrey.common.time.RelativeTimeRange;
import pbouda.jeffrey.jfrparser.api.record.StackBasedRecord;

import java.util.function.ToLongFunction;

public class SimpleTimeseriesBuilder extends TimeseriesBuilder<StackBasedRecord, TimeseriesData> {

    private final LongLongHashMap values;

    private final ToLongFunction<StackBasedRecord> valueExtractor;

    public SimpleTimeseriesBuilder(RelativeTimeRange timeRange) {
        this(timeRange, false);
    }

    public SimpleTimeseriesBuilder(RelativeTimeRange timeRange, boolean useWeight) {
        this.values = structure(timeRange);
        this.valueExtractor = useWeight
                ? StackBasedRecord::sampleWeight
                : StackBasedRecord::samples;
    }

    @Override
    protected void incrementCounter(StackBasedRecord event, long second) {
        values.addToValue(second, valueExtractor.applyAsLong(event));
    }

    @Override
    public TimeseriesData build() {
        return new TimeseriesData(TimeseriesUtils.buildSerie("Samples", values));
    }
}
