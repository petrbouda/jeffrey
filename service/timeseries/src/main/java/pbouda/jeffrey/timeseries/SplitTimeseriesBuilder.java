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

import org.eclipse.collections.impl.map.mutable.primitive.LongBooleanHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import pbouda.jeffrey.common.time.RelativeTimeRange;
import pbouda.jeffrey.jfrparser.api.record.StackBasedRecord;
import pbouda.jeffrey.jfrparser.api.type.JfrStackTrace;
import pbouda.jeffrey.jfrparser.api.type.JfrThread;

import java.util.function.ToLongFunction;

public abstract class SplitTimeseriesBuilder<T extends StackBasedRecord> extends TimeseriesBuilder<T, TimeseriesData> {

    private final LongLongHashMap values;
    private final LongLongHashMap matchedValues;
    private final LongBooleanHashMap processed = new LongBooleanHashMap();

    private final ToLongFunction<T> valueExtractor;

    public SplitTimeseriesBuilder(RelativeTimeRange timeRange, boolean useWeight) {
        this.values = structure(timeRange);
        this.matchedValues = structure(timeRange);
        this.valueExtractor = useWeight
                ? T::sampleWeight
                : T::samples;
    }

    @Override
    protected void incrementCounter(T event, long second) {
        if (processStacktrace(event)) {
            matchedValues.addToValue(second, valueExtractor.applyAsLong(event));
            values.getIfAbsentPut(second, 0);
        } else {
            values.addToValue(second, valueExtractor.applyAsLong(event));
            matchedValues.getIfAbsentPut(second, 0);
        }
    }

    private boolean processStacktrace(T event) {
        JfrStackTrace stacktrace = event.stackTrace();
        JfrThread thread = event.thread();
        if (stacktrace != null) {
            return processed.getIfAbsentPutWithKey(stacktrace.id(), __ -> matchesStacktrace(stacktrace, thread));
        }
        return false;
    }

    protected abstract boolean matchesStacktrace(JfrStackTrace stacktrace, JfrThread thread);

    @Override
    public TimeseriesData build() {
        return new TimeseriesData(
                TimeseriesUtils.buildSerie("Samples", values),
                TimeseriesUtils.buildSerie("Matched Samples", matchedValues));
    }
}
