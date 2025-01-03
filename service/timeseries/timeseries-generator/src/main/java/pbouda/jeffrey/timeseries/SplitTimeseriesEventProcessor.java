/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedStackTrace;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.Type;

import java.time.Duration;
import java.util.IdentityHashMap;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

public abstract class SplitTimeseriesEventProcessor extends TimeseriesEventProcessor<TimeseriesMaps> {

    private final LongLongHashMap values = new LongLongHashMap();
    private final LongLongHashMap matchedValues = new LongLongHashMap();
    private final IdentityHashMap<RecordedStackTrace, Boolean> processed = new IdentityHashMap<>();
    private final ToLongFunction<RecordedEvent> valueExtractor;

    public SplitTimeseriesEventProcessor(
            Type eventType,
            ToLongFunction<RecordedEvent> valueExtractor,
            AbsoluteTimeRange absoluteTimeRange,
            Duration timeShift,
            Predicate<RecordedEvent> filtering) {

        super(eventType, absoluteTimeRange, timeShift, filtering);
        this.valueExtractor = valueExtractor;
    }

    @Override
    protected void incrementCounter(RecordedEvent event, long second) {
        if (processStacktrace(event)) {
            matchedValues.addToValue(second, valueExtractor.applyAsLong(event));
            values.getIfAbsentPut(second, 0);
        } else {
            values.addToValue(second, valueExtractor.applyAsLong(event));
            matchedValues.getIfAbsentPut(second, 0);
        }
    }

    private boolean processStacktrace(RecordedEvent event) {
        RecordedStackTrace stacktrace = event.getStackTrace();
        if (stacktrace != null) {
            return processed.computeIfAbsent(stacktrace, __ -> matchesStacktrace(event, stacktrace));
        }
        return false;
    }

    /**
     * Implement the logic to match the stacktrace and split the samples into two timeseries.
     *
     * @param event      the event to match
     * @param stacktrace the stacktrace to match
     * @return `true` if the stacktrace matches, `false` otherwise
     */
    protected abstract boolean matchesStacktrace(RecordedEvent event, RecordedStackTrace stacktrace);

    @Override
    public TimeseriesMaps get() {
        return new TimeseriesMaps(values, matchedValues);
    }
}
