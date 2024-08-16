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
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import org.eclipse.collections.api.map.primitive.MutableObjectBooleanMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectBooleanHashMap;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.Type;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class SearchableTimeseriesEventProcessor extends TimeseriesEventProcessor<SearchMaps> {

    private final LongLongHashMap values = new LongLongHashMap();
    private final LongLongHashMap matchedValues = new LongLongHashMap();
    private final Predicate<String> searchPredicate;
    private final MutableObjectBooleanMap<RecordedStackTrace> processed = new ObjectBooleanHashMap<>();

    public SearchableTimeseriesEventProcessor(
            Type eventType,
            Function<RecordedEvent, Long> valueExtractor,
            AbsoluteTimeRange absoluteTimeRange,
            String searchPattern) {

        this(eventType, valueExtractor, absoluteTimeRange, searchPattern, 0);
    }

    public SearchableTimeseriesEventProcessor(
            Type eventType,
            Function<RecordedEvent, Long> valueExtractor,
            AbsoluteTimeRange absoluteTimeRange,
            String searchPattern,
            long timeShift) {

        super(eventType, valueExtractor, absoluteTimeRange, timeShift);
        this.searchPredicate = Pattern.compile(".*" + searchPattern + ".*").asMatchPredicate();
    }

    @Override
    protected void incrementCounter(RecordedEvent event, long second) {
        if (matchesStacktrace(event.getStackTrace(), searchPredicate)) {
            matchedValues.addToValue(second, valueExtractor.apply(event));
            values.getIfAbsentPut(second, 0);
        } else {
            values.addToValue(second, valueExtractor.apply(event));
            matchedValues.getIfAbsentPut(second, 0);
        }
    }

    private boolean matchesStacktrace(RecordedStackTrace stacktrace, Predicate<String> searchPredicate) {
        if (stacktrace != null) {
            return processed.getIfAbsentPut(stacktrace, () -> {
                for (RecordedFrame frame : stacktrace.getFrames()) {
                    if (matchesMethod(frame.getMethod(), searchPredicate)) {
                        return true;
                    }
                }
                return false;
            });
        }
        return false;
    }

    private static boolean matchesMethod(RecordedMethod method, Predicate<String> searchPredicate) {
        if (method.getType() != null) {
            return searchPredicate.test(method.getType().getName() + "#" + method.getName());
        } else {
            return searchPredicate.test(method.getName());
        }
    }

    @Override
    public SearchMaps get() {
        return new SearchMaps(values, matchedValues);
    }
}
