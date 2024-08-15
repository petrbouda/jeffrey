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

package pbouda.jeffrey.generator.basic.info;

import jdk.jfr.EventType;
import pbouda.jeffrey.common.EventSource;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.basic.event.EventSummary;

import java.util.Map;

public class BlockingExtraInfo implements ExtraInfoEnhancer {

    private final Map<String, String> settings;

    public BlockingExtraInfo(Map<String, String> settings) {
        this.settings = settings;
    }

    @Override
    public boolean isApplicable(EventType eventType) {
        return Type.JAVA_MONITOR_ENTER.sameAs(eventType)
                || Type.THREAD_PARK.sameAs(eventType);
    }

    private static boolean recordedByAsyncProfiler(Map<String, String> settings) {
        return EventSource.ASYNC_PROFILER.name().equals(settings.get("source"));
    }

    @Override
    public EventSummary apply(EventSummary eventSummary) {
        if (recordedByAsyncProfiler(settings) && settings.containsKey("lock_event")) {
            return eventSummary.copyAndAddExtra("source", settings.get("source"));
        }

        return eventSummary;
    }
}
