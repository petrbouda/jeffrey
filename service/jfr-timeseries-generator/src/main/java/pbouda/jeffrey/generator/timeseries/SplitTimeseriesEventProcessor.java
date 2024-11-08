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

package pbouda.jeffrey.generator.timeseries;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedStackTrace;
import org.eclipse.collections.api.map.primitive.MutableObjectBooleanMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectBooleanHashMap;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.Type;

import java.util.function.Function;
import java.util.function.Predicate;

public abstract class SplitTimeseriesEventProcessor extends TimeseriesEventProcessor<TimeseriesMaps> {

    private final LongLongHashMap values = new LongLongHashMap();
    private final LongLongHashMap matchedValues = new LongLongHashMap();
    private final MutableObjectBooleanMap<RecordedStackTrace> processed = new ObjectBooleanHashMap<>();
    private final Function<RecordedEvent, Long> valueExtractor;

    public SplitTimeseriesEventProcessor(
            Type eventType,
            Function<RecordedEvent, Long> valueExtractor,
            AbsoluteTimeRange absoluteTimeRange,
            Predicate<RecordedEvent> filtering,
            long timeShift) {

        super(eventType, absoluteTimeRange, filtering, timeShift);
        this.valueExtractor = valueExtractor;
    }

    @Override
    protected void incrementCounter(RecordedEvent event, long second) {
        if (processStacktrace(event)) {
            matchedValues.addToValue(second, valueExtractor.apply(event));
            values.getIfAbsentPut(second, 0);
        } else {
            values.addToValue(second, valueExtractor.apply(event));
            matchedValues.getIfAbsentPut(second, 0);
        }
    }

    private boolean processStacktrace(RecordedEvent event) {
        RecordedStackTrace stacktrace = event.getStackTrace();
        if (stacktrace != null) {
            return processed.getIfAbsentPut(stacktrace, () -> matchesStacktrace(event, stacktrace));
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
