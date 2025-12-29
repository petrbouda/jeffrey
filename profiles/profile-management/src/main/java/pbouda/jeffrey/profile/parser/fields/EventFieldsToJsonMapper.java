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

package pbouda.jeffrey.profile.parser.fields;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.*;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedThread;
import pbouda.jeffrey.shared.Json;
import pbouda.jeffrey.shared.RecordedClassMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventFieldsToJsonMapper implements EventFieldsMapper {

    public static final List<String> IGNORED_FIELDS = List.of("stackTrace");

    private static final String TIMESTAMP_TYPE_NAME = Timestamp.class.getTypeName();
    private static final String PERCENTAGE_TYPE_NAME = Percentage.class.getTypeName();
    private static final String TIMESPAN_TYPE_NAME = Timespan.class.getTypeName();
    private static final String UNSIGNED_TYPE_NAME = Unsigned.class.getTypeName();

    private final Map<Long, EventType> eventTypes = new HashMap<>();

    @Override
    public void update(List<EventType> eventTypes) {
        eventTypes.forEach(e -> this.eventTypes.put(e.getId(), e));
    }

    @Override
    public ObjectNode map(RecordedEvent event) {
        ObjectNode node = Json.createObject();
        for (ValueDescriptor field : event.getFields()) {
            if (!IGNORED_FIELDS.contains(field.getName())) {
                if (handleByAnnotation(field, event, node)) {
                    // Handled by annotation, skip further processing
                    continue;
                }

                if ("java.lang.Thread".equals(field.getTypeName())) {
                    RecordedThread value = event.getThread(field.getName());
                    node.put(field.getName(), safeThreadToString(value));
                } else if ("java.lang.Class".equals(field.getTypeName())) {
                    RecordedClass clazz = event.getClass(field.getName());
                    node.put(field.getName(), RecordedClassMapper.map(clazz.getName()));
                } else if ("jdk.types.Method".equals(field.getTypeName())) {
                    RecordedMethod method = event.getValue(field.getName());
                    if (method != null) {
                        node.put(field.getName(), method.getType().getName() + "#" + method.getName());
                    }
                } else if ("jdk.ActiveSetting".equals(event.getEventType().getName()) && "id".equals(field.getName())) {
                    long eventId = event.getValue(field.getName());
                    node.put(field.getName(), eventId);
                    node.put("label", activeSettingValue(eventId));
                } else if ("long".equals(field.getTypeName()) || "int".equals(field.getTypeName())) {
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

    private static boolean handleByAnnotation(ValueDescriptor field, RecordedEvent event, ObjectNode node) {
        for (AnnotationElement annotation : field.getAnnotationElements()) {
            String typeName = annotation.getTypeName();
            if (typeName.equals(TIMESTAMP_TYPE_NAME)) {
                Instant instant = event.getInstant(field.getName());
                node.put(field.getName(), safeToLongMillis(instant));
                return true;
            } else if (typeName.equals(PERCENTAGE_TYPE_NAME)) {
                float value = event.getFloat(field.getName());
                node.put(field.getName(), value);
                return true;
            } else if (typeName.equals(TIMESPAN_TYPE_NAME)) {
                Duration value = event.getDuration(field.getName());
                node.put(field.getName(), safeDurationToLongNanos(value));
                return true;
            }
        }
        return false;
    }

    private String activeSettingValue(long eventId) {
        EventType eventType = eventTypes.get(eventId);
        return eventType == null ? "Unknown (eventId=" + eventId + ")" : eventType.getLabel();
    }

    private static String safeToString(Object val) {
        return val == null ? null : val.toString();
    }

    private static Long safeToLongNanos(Duration value) {
        return value.isNegative() ? null : value.toNanos();
    }

    private static Long safeDurationToLongNanos(Duration value) {
        if (value.getSeconds() == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        } else if (value == Duration.ZERO) {
            return null;
        } else {
            return safeToLongNanos(value);
        }
    }

    private static Long safeToLongMillis(Instant value) {
        return value == Instant.MIN ? null : value.toEpochMilli();
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
