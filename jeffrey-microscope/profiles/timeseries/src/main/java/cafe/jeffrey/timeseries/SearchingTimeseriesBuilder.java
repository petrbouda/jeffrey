/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.timeseries;

import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.jfrparser.api.type.JfrMethod;
import cafe.jeffrey.jfrparser.api.type.JfrStackFrame;
import cafe.jeffrey.jfrparser.api.type.JfrStackTrace;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public class SearchingTimeseriesBuilder extends SplitTimeseriesBuilder {

    private final Predicate<String> searchPredicate;

    public SearchingTimeseriesBuilder(RelativeTimeRange timeRange, String searchPattern) {
        super(timeRange);
        this.searchPredicate = Pattern.compile(".*" + searchPattern + ".*").asMatchPredicate();
    }

    @Override
    protected boolean matchesStacktrace(JfrStackTrace stacktrace) {
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
