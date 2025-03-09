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

import pbouda.jeffrey.common.time.RelativeTimeRange;
import pbouda.jeffrey.jfrparser.api.record.StackBasedRecord;
import pbouda.jeffrey.jfrparser.api.type.JfrMethod;
import pbouda.jeffrey.jfrparser.api.type.JfrStackFrame;
import pbouda.jeffrey.jfrparser.api.type.JfrStackTrace;
import pbouda.jeffrey.jfrparser.api.type.JfrThread;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public class SearchableTimeseriesBuilder extends SplitTimeseriesBuilder<StackBasedRecord> {

    private final Predicate<String> searchPredicate;

    public SearchableTimeseriesBuilder(RelativeTimeRange timeRange, String searchPattern, boolean useWeight) {
        super(timeRange, useWeight);
        this.searchPredicate = Pattern.compile(".*" + searchPattern + ".*").asMatchPredicate();
    }

    @Override
    protected boolean matchesStacktrace(JfrStackTrace stacktrace, JfrThread thread) {
        for (JfrStackFrame frame : stacktrace.frames()) {
            if (matchesMethod(frame.method())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesMethod(JfrMethod method) {
        if (method.clazz() != null) {
            return searchPredicate.test(method.className() + "#" + method.methodName());
        } else {
            return searchPredicate.test(method.methodName());
        }
    }
}
