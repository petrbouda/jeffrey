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

package pbouda.jeffrey.frameir.processor.filter;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedStackTrace;

import java.util.IdentityHashMap;
import java.util.Map;

public class CachingFilter implements EventProcessorFilter {

    private final Map<RecordedStackTrace, Boolean> processed = new IdentityHashMap<>();
    private final EventProcessorFilter filter;

    public CachingFilter(EventProcessorFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean test(RecordedEvent event) {
        RecordedStackTrace stacktrace = event.getStackTrace();
        Boolean filtered = processed.get(stacktrace);
        if (filtered == null) {
            filtered = filter.test(event);
            processed.put(stacktrace, filtered);
        }
        return filtered;
    }
}
