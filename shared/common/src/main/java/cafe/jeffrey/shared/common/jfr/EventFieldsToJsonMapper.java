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

package cafe.jeffrey.shared.common.jfr;

import tools.jackson.databind.node.ObjectNode;
import jdk.jfr.AnnotationElement;
import jdk.jfr.EventType;
import jdk.jfr.Percentage;
import jdk.jfr.Timespan;
import jdk.jfr.Timestamp;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedThread;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.RecordedClassMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EventFieldsToJsonMapper implements EventFieldsMapper {

    public static final List<String> IGNORED_FIELDS = List.of("stackTrace");

    private static final String TIMESTAMP_TYPE_NAME = Timestamp.class.getTypeName();
    private static final String PERCENTAGE_TYPE_NAME = Percentage.class.getTypeName();
    private static final String TIMESPAN_TYPE_NAME = Timespan.class.getTypeName();

    private static final String THREAD_TYPE_NAME = "java.lang.Thread";
    private static final String CLASS_TYPE_NAME = "java.lang.Class";
    private static final String CLASS_LOADER_TYPE_NAME = "jdk.types.ClassLoader";
    private static final String CLASS_LOADER_TYPE_FIELD = "type";
    private static final String CLASS_LOADER_NAME_FIELD = "name";
    private static final String OLD_OBJECT_TYPE_NAME = "jdk.types.OldObject";
    private static final String OLD_OBJECT_TYPE_FIELD = "type";
    private static final String METHOD_TYPE_NAME = "jdk.types.Method";
    private static final String MODULE_TYPE_NAME = "jdk.types.Module";
    private static final String PACKAGE_TYPE_NAME = "jdk.types.Package";
    private static final String STRUCT_NAME_FIELD = "name";
    private static final String BOOLEAN_TYPE_NAME = "boolean";
    private static final Set<String> INTEGRAL_TYPE_NAMES = Set.of("long", "int");

    private static final String ACTIVE_SETTING_EVENT_NAME = "jdk.ActiveSetting";
    private static final String ACTIVE_SETTING_ID_FIELD = "id";
    private static final String ACTIVE_SETTING_LABEL_FIELD = "label";

    /**
     * Writes a single event field into the JSON node. Resolved once per
     * {@link EventType} field (annotations and type-name comparisons happen
     * at plan-build time), then reused for every event of that type.
     */
    @FunctionalInterface
    private interface FieldWriter {
        void write(RecordedEvent event, ObjectNode node);
    }

    /**
     * Ordered field writers for one {@link EventType}, in the event's field order.
     */
    private record EventTypePlan(List<FieldWriter> writers) {
    }

    private final Map<Long, EventType> eventTypes = new HashMap<>();
    private final Map<Long, EventTypePlan> plansByEventTypeId = new HashMap<>();

    @Override
    public void update(List<EventType> eventTypes) {
        for (EventType eventType : eventTypes) {
            this.eventTypes.put(eventType.getId(), eventType);
            this.plansByEventTypeId.put(eventType.getId(), buildPlan(eventType));
        }
    }

    @Override
    public ObjectNode map(RecordedEvent event) {
        EventType eventType = event.getEventType();
        EventTypePlan plan = plansByEventTypeId.get(eventType.getId());
        if (plan == null) {
            // Metadata for this type has not been seen yet — build lazily and cache.
            plan = buildPlan(eventType);
            plansByEventTypeId.put(eventType.getId(), plan);
        }

        ObjectNode node = Json.createObject();
        for (FieldWriter writer : plan.writers()) {
            writer.write(event, node);
        }
        return node;
    }

    private EventTypePlan buildPlan(EventType eventType) {
        boolean activeSettingEvent = ACTIVE_SETTING_EVENT_NAME.equals(eventType.getName());

        List<FieldWriter> writers = new ArrayList<>();
        for (ValueDescriptor field : eventType.getFields()) {
            if (!IGNORED_FIELDS.contains(field.getName())) {
                writers.add(resolveWriter(field, activeSettingEvent));
            }
        }
        return new EventTypePlan(List.copyOf(writers));
    }

    private FieldWriter resolveWriter(ValueDescriptor field, boolean activeSettingEvent) {
        FieldWriter annotationWriter = resolveAnnotationWriter(field);
        if (annotationWriter != null) {
            return annotationWriter;
        }

        String name = field.getName();
        String typeName = field.getTypeName();

        if (THREAD_TYPE_NAME.equals(typeName)) {
            return (event, node) -> {
                RecordedThread value = event.getThread(name);
                node.put(name, safeThreadToString(value));
            };
        } else if (CLASS_TYPE_NAME.equals(typeName)) {
            return (event, node) -> {
                RecordedClass clazz = event.getClass(name);
                node.put(name, RecordedClassMapper.map(clazz.getName()));
            };
        } else if (CLASS_LOADER_TYPE_NAME.equals(typeName)) {
            // The JFR ClassLoader struct ({type: Class, name: String}) has no special
            // RecordedObject handling and would otherwise fall through to a verbose
            // toString() dump. Flatten it to a readable identity label instead.
            return (event, node) -> node.put(name, classLoaderLabel(event.getValue(name)));
        } else if (OLD_OBJECT_TYPE_NAME.equals(typeName)) {
            // The JFR OldObject struct ({type: Class, address, referrer, …}) has no special
            // handling and would fall through to a verbose toString() dump. Flatten it to the
            // leaked object's class name — the field the leak-candidates view needs.
            return (event, node) -> node.put(name, oldObjectType(event.getValue(name)));
        } else if (METHOD_TYPE_NAME.equals(typeName)) {
            return (event, node) -> {
                RecordedMethod method = event.getValue(name);
                if (method != null) {
                    node.put(name, method.getType().getName() + "#" + method.getName());
                }
            };
        } else if (MODULE_TYPE_NAME.equals(typeName) || PACKAGE_TYPE_NAME.equals(typeName)) {
            // The JFR Module/Package structs would otherwise fall through to a verbose toString() dump.
            // Flatten each to its identifying name (the module/package name); absent for the unnamed module.
            return (event, node) -> node.put(name, structName(event.getValue(name)));
        } else if (activeSettingEvent && ACTIVE_SETTING_ID_FIELD.equals(name)) {
            return (event, node) -> {
                long eventId = event.getValue(name);
                node.put(name, eventId);
                node.put(ACTIVE_SETTING_LABEL_FIELD, activeSettingValue(eventId));
            };
        } else if (INTEGRAL_TYPE_NAMES.contains(typeName)) {
            return (event, node) -> {
                long value = event.getLong(name);
                node.put(name, value);
            };
        } else if (BOOLEAN_TYPE_NAME.equals(typeName)) {
            return (event, node) -> {
                boolean value = event.getBoolean(name);
                node.put(name, value);
            };
        } else {
            return (event, node) -> {
                String value = safeToString(event.getValue(name));
                node.put(name, value);
            };
        }
    }

    /**
     * Mirrors the legacy per-event annotation scan: the first annotation on the
     * field that is one of {@code Timestamp}, {@code Percentage}, {@code Timespan}
     * (in the field's annotation order) decides the writer. Returns {@code null}
     * when no relevant annotation is present.
     */
    private static FieldWriter resolveAnnotationWriter(ValueDescriptor field) {
        String name = field.getName();
        for (AnnotationElement annotation : field.getAnnotationElements()) {
            String typeName = annotation.getTypeName();
            if (typeName.equals(TIMESTAMP_TYPE_NAME)) {
                return (event, node) -> {
                    Instant instant = event.getInstant(name);
                    node.put(name, safeToLongMillis(instant));
                };
            } else if (typeName.equals(PERCENTAGE_TYPE_NAME)) {
                return (event, node) -> {
                    float value = event.getFloat(name);
                    node.put(name, value);
                };
            } else if (typeName.equals(TIMESPAN_TYPE_NAME)) {
                return (event, node) -> {
                    Duration value = event.getDuration(name);
                    node.put(name, safeDurationToLongNanos(value));
                };
            }
        }
        return null;
    }

    private String activeSettingValue(long eventId) {
        EventType eventType = eventTypes.get(eventId);
        return eventType == null ? "Unknown (eventId=" + eventId + ")" : eventType.getLabel();
    }

    private static String safeToString(Object val) {
        return val == null ? null : val.toString();
    }

    /**
     * Flattens a JFR {@code jdk.types.ClassLoader} struct to a single human-readable
     * label. Produces {@code "<type> (<name>)"} when the loader carries an instance
     * name, the bare type name otherwise, and {@code null} for the bootstrap loader
     * (the field value is absent for bootstrap-loaded classes).
     */
    private static String classLoaderLabel(Object value) {
        if (!(value instanceof RecordedObject loader)) {
            return null;
        }
        RecordedClass type = loader.getValue(CLASS_LOADER_TYPE_FIELD);
        String typeName = type == null ? null : RecordedClassMapper.map(type.getName());
        String name = loader.getString(CLASS_LOADER_NAME_FIELD);
        if (typeName == null) {
            return name;
        }
        if (name == null || name.isBlank()) {
            return typeName;
        }
        return typeName + " (" + name + ")";
    }

    /**
     * Flattens a JFR struct that carries a {@code name} field (e.g. {@code jdk.types.Module},
     * {@code jdk.types.Package}) to that name, or {@code null} when the struct is absent (the unnamed
     * module / a qualified export with no target).
     */
    private static String structName(Object value) {
        if (!(value instanceof RecordedObject struct)) {
            return null;
        }
        return struct.getString(STRUCT_NAME_FIELD);
    }

    /**
     * Flattens a JFR {@code jdk.types.OldObject} struct to the leaked object's class name (the
     * struct's {@code type} field), or {@code null} when unavailable.
     */
    private static String oldObjectType(Object value) {
        if (!(value instanceof RecordedObject oldObject)) {
            return null;
        }
        RecordedClass type = oldObject.getValue(OLD_OBJECT_TYPE_FIELD);
        return type == null ? null : RecordedClassMapper.map(type.getName());
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
