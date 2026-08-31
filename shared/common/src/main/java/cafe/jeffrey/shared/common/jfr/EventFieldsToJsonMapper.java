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
import tools.jackson.core.JsonGenerator;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.RecordedClassMapper;

import java.io.Writer;
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
    private static final String G1_EVACUATION_STATISTICS_TYPE_NAME = "jdk.types.G1EvacuationStatistics";
    private static final List<String> G1_EVACUATION_STAT_FIELDS = List.of(
            "gcId", "allocated", "wasted", "used", "undoWaste", "regionEndWaste",
            "regionsRefilled", "numPlabsFilled", "directAllocated", "numDirectAllocated",
            "failureUsed", "failureWaste");
    private static final String BOOLEAN_TYPE_NAME = "boolean";
    private static final Set<String> INTEGRAL_TYPE_NAMES = Set.of("long", "int");

    /**
     * Below this size, inline storage beats pooling: the reference and the dictionary row cost more
     * to store and to join than the repeated short text costs to keep. At or above it, a repeated
     * value compresses to one row and a value that never repeats costs only its reference.
     */
    private static final int MIN_POOLED_LENGTH = 64;

    /** Wide enough for a typical event's JSON, so the buffer stops growing almost immediately. */
    private static final int INITIAL_JSON_CAPACITY = 512;

    private static final String ACTIVE_SETTING_EVENT_NAME = "jdk.ActiveSetting";
    private static final String ACTIVE_SETTING_ID_FIELD = "id";
    private static final String ACTIVE_SETTING_LABEL_FIELD = "label";

    /**
     * Reads a single event field into the slots. Resolved once per {@link EventType} field
     * (annotations and type-name comparisons happen at plan-build time), then reused for every
     * event of that type. One field may contribute more than one slot, or none at all.
     */
    @FunctionalInterface
    private interface FieldEvaluator {
        void evaluate(RecordedEvent event, FieldSlots slots);
    }

    /**
     * Ordered field evaluators for one {@link EventType}, in the event's field order.
     */
    private record EventTypePlan(List<FieldEvaluator> evaluators) {
    }

    private final Map<Long, EventType> eventTypes = new HashMap<>();
    private final Map<Long, EventTypePlan> plansByEventTypeId = new HashMap<>();

    // Reused for every event: this class is per parsing thread, and these are the only two
    // allocations the old tree-based mapping made per event that were not the JSON itself.
    private final FieldSlots slots = new FieldSlots();
    private final StringBuilder buffer = new StringBuilder(INITIAL_JSON_CAPACITY);
    private final Writer bufferWriter = new StringBuilderWriter(buffer);

    @Override
    public void update(List<EventType> eventTypes) {
        for (EventType eventType : eventTypes) {
            this.eventTypes.put(eventType.getId(), eventType);
            this.plansByEventTypeId.put(eventType.getId(), buildPlan(eventType));
        }
    }

    @Override
    public MappedFields map(RecordedEvent event) {
        EventType eventType = event.getEventType();
        EventTypePlan plan = plansByEventTypeId.get(eventType.getId());
        if (plan == null) {
            // Metadata for this type has not been seen yet — build lazily and cache.
            plan = buildPlan(eventType);
            plansByEventTypeId.put(eventType.getId(), plan);
        }

        slots.reset();
        for (FieldEvaluator evaluator : plan.evaluators()) {
            evaluator.evaluate(event, slots);
        }

        // Decided before the JSON is written, so the pooled value is simply never written rather
        // than written and then removed.
        int pooledSlot = slots.largestPoolableString(MIN_POOLED_LENGTH);

        buffer.setLength(0);
        try (JsonGenerator generator = Json.mapper().createGenerator(bufferWriter)) {
            slots.writeJson(generator, pooledSlot);
        }

        if (pooledSlot == FieldSlots.NO_SLOT) {
            return new MappedFields(buffer.toString(), null, null);
        }
        return new MappedFields(buffer.toString(), slots.name(pooledSlot), slots.string(pooledSlot));
    }

    private EventTypePlan buildPlan(EventType eventType) {
        boolean activeSettingEvent = ACTIVE_SETTING_EVENT_NAME.equals(eventType.getName());

        List<FieldEvaluator> evaluators = new ArrayList<>();
        for (ValueDescriptor field : eventType.getFields()) {
            if (!IGNORED_FIELDS.contains(field.getName())) {
                evaluators.add(resolveEvaluator(field, activeSettingEvent));
            }
        }
        return new EventTypePlan(List.copyOf(evaluators));
    }

    private FieldEvaluator resolveEvaluator(ValueDescriptor field, boolean activeSettingEvent) {
        FieldEvaluator annotationWriter = resolveAnnotationEvaluator(field);
        if (annotationWriter != null) {
            return annotationWriter;
        }

        String name = field.getName();
        String typeName = field.getTypeName();

        if (THREAD_TYPE_NAME.equals(typeName)) {
            return (event, slots) -> {
                RecordedThread value = event.getThread(name);
                slots.putString(name, safeThreadToString(value));
            };
        } else if (CLASS_TYPE_NAME.equals(typeName)) {
            return (event, slots) -> {
                RecordedClass clazz = event.getClass(name);
                slots.putString(name, safeClassName(clazz));
            };
        } else if (CLASS_LOADER_TYPE_NAME.equals(typeName)) {
            // The JFR ClassLoader struct ({type: Class, name: String}) has no special
            // RecordedObject handling and would otherwise fall through to a verbose
            // toString() dump. Flatten it to a readable identity label instead.
            return (event, slots) -> slots.putString(name, classLoaderLabel(event.getValue(name)));
        } else if (OLD_OBJECT_TYPE_NAME.equals(typeName)) {
            // The JFR OldObject struct ({type: Class, address, referrer, …}) has no special
            // handling and would fall through to a verbose toString() dump. Flatten it to the
            // leaked object's class name — the field the leak-candidates view needs.
            return (event, slots) -> slots.putString(name, oldObjectType(event.getValue(name)));
        } else if (METHOD_TYPE_NAME.equals(typeName)) {
            return (event, slots) -> {
                RecordedMethod method = event.getValue(name);
                if (method != null) {
                    slots.putString(name, method.getType().getName() + "#" + method.getName());
                }
            };
        } else if (MODULE_TYPE_NAME.equals(typeName) || PACKAGE_TYPE_NAME.equals(typeName)) {
            // The JFR Module/Package structs would otherwise fall through to a verbose toString() dump.
            // Flatten each to its identifying name (the module/package name); absent for the unnamed module.
            return (event, slots) -> slots.putString(name, structName(event.getValue(name)));
        } else if (G1_EVACUATION_STATISTICS_TYPE_NAME.equals(typeName)) {
            // The G1 PLAB statistics struct carries ~12 numeric sub-fields; flatten each to a dotted
            // key (e.g. "statistics.allocated") instead of dumping the whole struct via toString().
            return (event, slots) -> flattenG1EvacuationStatistics(event.getValue(name), name, slots);
        } else if (activeSettingEvent && ACTIVE_SETTING_ID_FIELD.equals(name)) {
            return (event, slots) -> {
                long eventId = event.getValue(name);
                slots.putLong(name, eventId);
                slots.putString(ACTIVE_SETTING_LABEL_FIELD, activeSettingValue(eventId));
            };
        } else if (INTEGRAL_TYPE_NAMES.contains(typeName)) {
            return (event, slots) -> {
                long value = event.getLong(name);
                slots.putLong(name, value);
            };
        } else if (BOOLEAN_TYPE_NAME.equals(typeName)) {
            return (event, slots) -> {
                boolean value = event.getBoolean(name);
                slots.putBoolean(name, value);
            };
        } else {
            return (event, slots) -> {
                String value = safeToString(event.getValue(name));
                slots.putString(name, value);
            };
        }
    }

    /**
     * Mirrors the legacy per-event annotation scan: the first annotation on the
     * field that is one of {@code Timestamp}, {@code Percentage}, {@code Timespan}
     * (in the field's annotation order) decides the writer. Returns {@code null}
     * when no relevant annotation is present.
     */
    private static FieldEvaluator resolveAnnotationEvaluator(ValueDescriptor field) {
        String name = field.getName();
        for (AnnotationElement annotation : field.getAnnotationElements()) {
            String typeName = annotation.getTypeName();
            if (typeName.equals(TIMESTAMP_TYPE_NAME)) {
                return (event, slots) -> {
                    Instant instant = event.getInstant(name);
                    slots.putLong(name, safeToLongMillis(instant));
                };
            } else if (typeName.equals(PERCENTAGE_TYPE_NAME)) {
                return (event, slots) -> {
                    float value = event.getFloat(name);
                    slots.putFloat(name, value);
                };
            } else if (typeName.equals(TIMESPAN_TYPE_NAME)) {
                return (event, slots) -> {
                    Duration value = event.getDuration(name);
                    slots.putLong(name, safeDurationToLongNanos(value));
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
     * A {@code java.lang.Class} field can legitimately be absent — {@code jdk.ThreadPark.parkedClass}
     * is null when {@code LockSupport.park()} is called with no blocker, and a monitor event can be
     * emitted with no monitor class. Such a field lands as an explicit JSON null rather than failing
     * the whole recording.
     */
    private static String safeClassName(RecordedClass clazz) {
        return clazz == null ? null : RecordedClassMapper.map(clazz.getName());
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
     * Flattens the JFR {@code jdk.types.G1EvacuationStatistics} struct (carried by
     * {@code jdk.G1EvacuationYoungStatistics} / {@code jdk.G1EvacuationOldStatistics}) by lifting each of
     * its numeric sub-fields to a dotted key under the parent field name, e.g.
     * {@code statistics.allocated}. Absent when the struct is missing.
     */
    private static void flattenG1EvacuationStatistics(Object value, String fieldName, FieldSlots slots) {
        if (!(value instanceof RecordedObject struct)) {
            return;
        }
        for (String subField : G1_EVACUATION_STAT_FIELDS) {
            if (struct.hasField(subField)) {
                slots.putLong(fieldName + "." + subField, struct.getLong(subField));
            }
        }
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

    /**
     * Lets the JSON generator write straight into a {@link StringBuilder} the mapper reuses, rather
     * than into a fresh writer and its buffers per event. Flushing and closing are no-ops: the
     * builder is the destination, there is nothing underneath it to push to.
     */
    private static final class StringBuilderWriter extends Writer {

        private final StringBuilder builder;

        private StringBuilderWriter(StringBuilder builder) {
            this.builder = builder;
        }

        @Override
        public void write(char[] chars, int offset, int length) {
            builder.append(chars, offset, length);
        }

        @Override
        public void write(String text) {
            builder.append(text);
        }

        @Override
        public void write(String text, int offset, int length) {
            builder.append(text, offset, offset + length);
        }

        @Override
        public void write(int character) {
            builder.append((char) character);
        }

        @Override
        public void flush() {
            // Nothing to flush to — the builder is the destination.
        }

        @Override
        public void close() {
            // The builder outlives every generator that writes into it.
        }
    }
}
