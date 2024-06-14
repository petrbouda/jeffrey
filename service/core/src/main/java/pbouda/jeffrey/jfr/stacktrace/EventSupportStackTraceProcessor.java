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

package pbouda.jeffrey.jfr.stacktrace;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.ProcessableEvents;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class EventSupportStackTraceProcessor implements EventProcessor, Supplier<Set<EventType>> {

    private final Set<EventType> result = new HashSet<>();

    @Override
    public ProcessableEvents processableEvents() {
        return ProcessableEvents.all();
    }

    @Override
    public EventProcessor.Result onEvent(RecordedEvent event) {
        EventType eventType = event.getEventType();
        if (!result.contains(eventType) && event.getStackTrace() != null) {
            result.add(eventType);
        }

        return Result.CONTINUE;
    }

    @Override
    public Set<EventType> get() {
        return result;
    }
}
