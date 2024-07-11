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

package pbouda.jeffrey.jfr.info;

import jdk.jfr.EventType;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfr.EventSource;
import pbouda.jeffrey.jfr.event.EventSummary;

import java.util.Map;

public class AllocationSamplesExtraInfo implements ExtraInfoEnhancer {

    private final Map<String, String> settings;

    public AllocationSamplesExtraInfo(Map<String, String> settings) {
        this.settings = settings;
    }

    @Override
    public boolean isApplicable(EventType eventType) {
        return Type.OBJECT_ALLOCATION_IN_NEW_TLAB.sameAs(eventType);
    }

    private static boolean recordedByAsyncProfiler(Map<String, String> settings) {
        return EventSource.ASYNC_PROFILER.name().equals(settings.get("source"));
    }

    @Override
    public EventSummary apply(EventSummary eventSummary) {
        if (recordedByAsyncProfiler(settings) && settings.containsKey("alloc_event")) {
            return eventSummary.copyAndAddExtra("source", settings.get("source"));
        }

        return eventSummary;
    }
}
