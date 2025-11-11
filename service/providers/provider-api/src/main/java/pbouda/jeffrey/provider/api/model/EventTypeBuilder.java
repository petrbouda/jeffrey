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

package pbouda.jeffrey.provider.api.model;


import pbouda.jeffrey.common.model.RecordingEventSource;
import pbouda.jeffrey.provider.api.model.writer.EnhancedEventType;

import java.util.HashMap;
import java.util.Map;

public class EventTypeBuilder {

    private EventType eventType;
    private RecordingEventSource source = RecordingEventSource.JDK;
    private String subtype;
    private boolean calculated = false;
    private boolean containsStackTraces = false;
    private Map<String, String> extras;
    private Map<String, String> params;

    public static EventTypeBuilder newBuilder(EventType eventType) {
        EventTypeBuilder builder = new EventTypeBuilder();
        builder.eventType = eventType;
        return builder;
    }

    public EventType getEventType() {
        return eventType;
    }

    public EventTypeBuilder withContainsStackTraces(boolean containsStackTraces) {
        this.containsStackTraces = containsStackTraces;
        return this;
    }

    public EventTypeBuilder withSource(RecordingEventSource source) {
        this.source = source;
        return this;
    }

    public EventTypeBuilder withSubtype(String subtype) {
        this.subtype = subtype;
        return this;
    }

    public EventTypeBuilder putExtras(Map<String, String> extras) {
        this.extras = new HashMap<>(extras);
        return this;
    }

    public EventTypeBuilder putParams(Map<String, String> params) {
        this.params = new HashMap<>(params);
        return this;
    }

    public EnhancedEventType build() {
        return new EnhancedEventType(
                eventType, source, subtype, containsStackTraces, extras, params);
    }
}
