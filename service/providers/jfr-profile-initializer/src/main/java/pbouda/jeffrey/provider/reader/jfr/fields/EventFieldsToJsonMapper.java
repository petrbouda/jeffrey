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

package pbouda.jeffrey.provider.reader.jfr.fields;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.EventType;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedThread;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.RecordedClassMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EventFieldsToJsonMapper {

    public static final List<String> IGNORED_FIELDS = List.of("stackTrace");

    private final Map<Long, EventType> eventTypes;

    public EventFieldsToJsonMapper(List<EventType> eventTypes) {
        this.eventTypes = eventTypes.stream()
                .collect(Collectors.toMap(EventType::getId, e -> e));
    }

    public ObjectNode map(RecordedEvent event) {
        ObjectNode node = Json.createObject();
        for (ValueDescriptor field : event.getFields()) {
            if (!IGNORED_FIELDS.contains(field.getName())) {
                if ("long".equals(field.getTypeName()) && "jdk.jfr.Timestamp".equals(field.getContentType())) {
                    Instant instant = event.getInstant(field.getName());
                    node.put(field.getName(), safeToLongMillis(instant));
                } else if ("jdk.jfr.Percentage".equals(field.getContentType())) {
                    float value = event.getFloat(field.getName());
                    node.put(field.getName(), value);
                } else if ("jdk.jfr.Timespan".equals(field.getContentType())) {
                    Duration value = event.getDuration(field.getName());
                    node.put(field.getName(), safeDurationToLongNanos(value));
                } else if ("java.lang.Thread".equals(field.getTypeName())) {
                    RecordedThread value = event.getThread(field.getName());
                    node.put(field.getName(), safeThreadToString(value));
                } else if ("java.lang.Class".equals(field.getTypeName())) {
                    RecordedClass clazz = event.getClass(field.getName());
                    node.put(field.getName(), RecordedClassMapper.map(clazz.getName()));
                } else if ("jdk.types.Method".equals(field.getTypeName())) {
                    RecordedMethod method = event.getValue(field.getName());
                    node.put(field.getName(), method.getType().getName() + "#" + method.getName());
                } else if ("jdk.ActiveSetting".equals(event.getEventType().getName())
                        && "id".equals(field.getName())) {
                    long eventId = event.getValue(field.getName());
                    node.put(field.getName(), eventId);
                    node.put("label", activeSettingValue(eventId));
                } else if ("long".equals(field.getTypeName())) {
                    long value = event.getLong(field.getName());
                    node.put(field.getName(), value);
                } else if ("boolean".equals(field.getTypeName())) {
                    boolean value = event.getBoolean(field.getName());
                    node.put(field.getName(), value);
                } else {
                    String value = safeToString(event.getValue(field.getName()));
                    node.put(field.getName(), value);
                }
            }
        }

        return node;
    }

    private String activeSettingValue(long eventId) {
        EventType eventType = eventTypes.get(eventId);
        return eventType == null ? "Unknown (eventId=" + eventId + ")" : eventType.getLabel();
    }

    private static String safeToString(Object val) {
        return val == null ? null : val.toString();
    }

    private static long safeToLongNanos(Duration value) {
        return value.isNegative() ? -1 : value.toNanos();
    }

    private static long safeDurationToLongNanos(Duration value) {
        if (value.getSeconds() == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        } else {
            return safeToLongNanos(value);
        }
    }

    private static long safeToLongMillis(Instant value) {
        return value == Instant.MIN ? 0 : value.toEpochMilli();
    }

    private static String safeThreadToString(RecordedThread value) {
        if (value == null) {
            return null;
        }

        String threadName = value.getJavaName() == null ? value.getOSName() : value.getJavaName();
        if (value.isVirtual()) {
            threadName = threadName + " (Virtual)";
        }

        return threadName;
    }
}
