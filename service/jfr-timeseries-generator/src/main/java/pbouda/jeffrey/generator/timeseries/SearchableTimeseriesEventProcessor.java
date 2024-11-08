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
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.Type;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class SearchableTimeseriesEventProcessor extends SplitTimeseriesEventProcessor {

    private final Predicate<String> searchPredicate;

    public SearchableTimeseriesEventProcessor(
            Type eventType,
            Function<RecordedEvent, Long> valueExtractor,
            AbsoluteTimeRange absoluteTimeRange,
            Predicate<RecordedEvent> filtering,
            String searchPattern) {

        this(eventType, valueExtractor, absoluteTimeRange, filtering, searchPattern, 0);
    }

    public SearchableTimeseriesEventProcessor(
            Type eventType,
            Function<RecordedEvent, Long> valueExtractor,
            AbsoluteTimeRange absoluteTimeRange,
            Predicate<RecordedEvent> filtering,
            String searchPattern,
            long timeShift) {

        super(eventType, valueExtractor, absoluteTimeRange, filtering, timeShift);
        this.searchPredicate = Pattern.compile(".*" + searchPattern + ".*").asMatchPredicate();
    }

    @Override
    protected boolean matchesStacktrace(RecordedEvent event, RecordedStackTrace stacktrace) {
        for (RecordedFrame frame : stacktrace.getFrames()) {
            if (matchesMethod(frame.getMethod())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesMethod(RecordedMethod method) {
        if (method.getType() != null) {
            return searchPredicate.test(method.getType().getName() + "#" + method.getName());
        } else {
            return searchPredicate.test(method.getName());
        }
    }
}
