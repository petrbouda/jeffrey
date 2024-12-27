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
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.Type;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

public class SimpleTimeseriesEventProcessor extends TimeseriesEventProcessor<LongLongHashMap> {

    private final LongLongHashMap values = new LongLongHashMap();
    private final ToLongFunction<RecordedEvent> valueExtractor;

    public SimpleTimeseriesEventProcessor(
            Type eventType,
            ToLongFunction<RecordedEvent> valueExtractor,
            AbsoluteTimeRange timeRange,
            Predicate<RecordedEvent> filtering) {

        this(eventType, valueExtractor, timeRange, Duration.ZERO, filtering);
    }

    public SimpleTimeseriesEventProcessor(
            Type eventType,
            ToLongFunction<RecordedEvent> valueExtractor,
            AbsoluteTimeRange timeRange,
            Duration timeShift,
            Predicate<RecordedEvent> filtering) {

        super(eventType, timeRange, timeShift, filtering);
        this.valueExtractor = valueExtractor;
    }


    protected void incrementCounter(RecordedEvent event, long second) {
        long samples = valueExtractor.applyAsLong(event);
        values.addToValue(second, samples);
    }

    @Override
    public LongLongHashMap get() {
        return values;
    }
}
