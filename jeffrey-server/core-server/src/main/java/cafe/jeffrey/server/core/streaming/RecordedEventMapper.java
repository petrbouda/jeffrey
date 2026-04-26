/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.server.core.streaming;

import jdk.jfr.AnnotationElement;
import jdk.jfr.Percentage;
import jdk.jfr.Timespan;
import jdk.jfr.Timestamp;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedThread;
import cafe.jeffrey.server.api.v1.StreamingEvent;
import cafe.jeffrey.server.api.v1.TypedValue;
import cafe.jeffrey.shared.common.RecordedClassMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Maps JFR {@link RecordedEvent} fields to proto {@link TypedValue} messages.
 * Handles JFR annotations ({@code @Timestamp}, {@code @Percentage}, {@code @Timespan}),
 * special object types (Thread, Class, Method), and primitives.
 *
 * <p>Follows the same type resolution as
 * {@code cafe.jeffrey.profile.parser.fields.EventFieldsToJsonMapper}.</p>
 */
public abstract  class RecordedEventMapper {

    private static final String TIMESTAMP_TYPE = Timestamp.class.getTypeName();
    private static final String PERCENTAGE_TYPE = Percentage.class.getTypeName();
    private static final String TIMESPAN_TYPE = Timespan.class.getTypeName();
    private static final List<String> IGNORED_FIELDS = List.of("stackTrace");

    /**
     * Converts a {@link RecordedEvent} to a proto {@link StreamingEvent}.
     */
    public static StreamingEvent toStreamingEvent(String sessionId, RecordedEvent event) {
        StreamingEvent.Builder builder = StreamingEvent.newBuilder()
                .setEventType(event.getEventType().getName())
                .setSessionId(sessionId)
                .setTimestamp(event.getStartTime().toEpochMilli());

        for (ValueDescriptor field : event.getFields()) {
            String name = field.getName();
            if (IGNORED_FIELDS.contains(name)) {
                continue;
            }

            TypedValue typedValue = mapField(field, event);
            if (typedValue != null) {
                builder.putFields(name, typedValue);
            }
        }

        return builder.build();
    }

    /**
     * Maps a single JFR field to a {@link TypedValue}, resolving annotations and types.
     */
    static TypedValue mapField(ValueDescriptor field, RecordedEvent event) {
        // Annotation-based types take priority
        for (AnnotationElement annotation : field.getAnnotationElements()) {
            String typeName = annotation.getTypeName();
            if (TIMESTAMP_TYPE.equals(typeName)) {
                Instant instant = event.getInstant(field.getName());
                return instant == Instant.MIN ? null : longValue(instant.toEpochMilli());
            } else if (PERCENTAGE_TYPE.equals(typeName)) {
                return floatValue(event.getFloat(field.getName()));
            } else if (TIMESPAN_TYPE.equals(typeName)) {
                Duration duration = event.getDuration(field.getName());
                return duration.isNegative() || duration == Duration.ZERO
                        ? null : longValue(duration.toNanos());
            }
        }

        // Special object types
        String typeName = field.getTypeName();
        if ("java.lang.Thread".equals(typeName)) {
            RecordedThread thread = event.getThread(field.getName());
            return thread == null ? null : stringValue(threadName(thread));
        } else if ("java.lang.Class".equals(typeName)) {
            RecordedClass clazz = event.getClass(field.getName());
            return clazz == null ? null : stringValue(RecordedClassMapper.map(clazz.getName()));
        } else if ("jdk.types.Method".equals(typeName)) {
            RecordedMethod method = event.getValue(field.getName());
            return method == null ? null : stringValue(method.getType().getName() + "#" + method.getName());
        }

        // Primitive types
        if ("long".equals(typeName) || "int".equals(typeName)) {
            return longValue(event.getLong(field.getName()));
        } else if ("boolean".equals(typeName)) {
            return boolValue(event.getBoolean(field.getName()));
        } else if ("float".equals(typeName) || "double".equals(typeName)) {
            return doubleValue(event.getDouble(field.getName()));
        }

        // Default: convert to string
        Object value = event.getValue(field.getName());
        return value == null ? null : stringValue(value.toString());
    }

    private static String threadName(RecordedThread thread) {
        String name = thread.getJavaName() == null ? thread.getOSName() : thread.getJavaName();
        return thread.isVirtual() ? name + " (Virtual)" : name;
    }

    public static TypedValue stringValue(String value) {
        return TypedValue.newBuilder().setStringValue(value).build();
    }

    public static TypedValue longValue(long value) {
        return TypedValue.newBuilder().setLongValue(value).build();
    }

    public static TypedValue doubleValue(double value) {
        return TypedValue.newBuilder().setDoubleValue(value).build();
    }

    public static TypedValue floatValue(float value) {
        return TypedValue.newBuilder().setFloatValue(value).build();
    }

    public static TypedValue boolValue(boolean value) {
        return TypedValue.newBuilder().setBoolValue(value).build();
    }
}
