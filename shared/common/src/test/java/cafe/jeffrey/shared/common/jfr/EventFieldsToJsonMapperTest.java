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
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Percentage;
import jdk.jfr.Recording;
import jdk.jfr.StackTrace;
import jdk.jfr.Timespan;
import jdk.jfr.Timestamp;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.RecordedClassMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Verifies that the plan-based {@link EventFieldsToJsonMapper} produces JSON
 * that is semantically identical to the original per-event implementation.
 * The original algorithm is kept here verbatim as a reference and every event
 * of a real JFR recording is compared node-by-node against it.
 */
class EventFieldsToJsonMapperTest {

    private static final String PROBE_EVENT_NAME = "test.jeffrey.MapperProbe";
    private static final String ACTIVE_SETTING_EVENT_NAME = "jdk.ActiveSetting";

    @Name(PROBE_EVENT_NAME)
    @Label("Mapper Probe")
    @StackTrace(false)
    static class MapperProbeEvent extends Event {

        @Timestamp(Timestamp.MILLISECONDS_SINCE_EPOCH)
        long markedTimestamp;

        @Timespan(Timespan.NANOSECONDS)
        long markedTimespan;

        @Percentage
        float markedPercentage;

        long longValue;

        int intValue;

        boolean boolValue;

        String stringValue;

        Class<?> classValue;

        Thread threadValue;
    }

    private static List<RecordedEvent> recordedEvents;
    private static List<EventType> recordedEventTypes;

    @BeforeAll
    static void recordEvents() throws IOException {
        Path dumpFile = Files.createTempFile("event-fields-mapper-test", ".jfr");
        try (Recording recording = new Recording()) {
            recording.enable(MapperProbeEvent.class);
            recording.enable(ACTIVE_SETTING_EVENT_NAME);
            recording.start();

            MapperProbeEvent event = new MapperProbeEvent();
            event.markedTimestamp = 1_750_000_000_123L;
            event.markedTimespan = 1_234_567L;
            event.markedPercentage = 0.42f;
            event.longValue = 42L;
            event.intValue = 7;
            event.boolValue = true;
            event.stringValue = "hello-jeffrey";
            event.classValue = String.class;
            event.threadValue = Thread.currentThread();
            event.commit();

            recording.stop();
            recording.dump(dumpFile);
        }

        recordedEvents = RecordingFile.readAllEvents(dumpFile);
        recordedEventTypes = recordedEvents.stream()
                .map(RecordedEvent::getEventType)
                .distinct()
                .toList();
        Files.deleteIfExists(dumpFile);
    }

    private static RecordedEvent probeEvent() {
        return recordedEvents.stream()
                .filter(e -> PROBE_EVENT_NAME.equals(e.getEventType().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Probe event was not recorded"));
    }

    @Nested
    class ProbeEventFields {

        @Test
        void mapsAnnotatedAndPrimitiveFields() {
            EventFieldsToJsonMapper mapper = new EventFieldsToJsonMapper();
            mapper.update(recordedEventTypes);

            ObjectNode node = mapper.map(probeEvent());

            assertEquals(1_750_000_000_123L, node.get("markedTimestamp").asLong());
            assertEquals(1_234_567L, node.get("markedTimespan").asLong());
            assertEquals(0.42f, node.get("markedPercentage").floatValue());
            assertEquals(42L, node.get("longValue").asLong());
            assertEquals(7L, node.get("intValue").asLong());
            assertTrue(node.get("boolValue").asBoolean());
            assertEquals("hello-jeffrey", node.get("stringValue").asString());
            assertEquals(RecordedClassMapper.map(String.class.getName()), node.get("classValue").asString());
            assertTrue(node.has("threadValue"));
            assertTrue(node.has("startTime"));
        }

        @Test
        void ignoresStacktraceField() {
            EventFieldsToJsonMapper mapper = new EventFieldsToJsonMapper();
            mapper.update(recordedEventTypes);

            ObjectNode node = mapper.map(probeEvent());

            assertFalse(node.has("stackTrace"));
        }

        @Test
        void buildsPlanLazilyWithoutMetadataUpdate() {
            EventFieldsToJsonMapper withMetadata = new EventFieldsToJsonMapper();
            withMetadata.update(recordedEventTypes);

            EventFieldsToJsonMapper withoutMetadata = new EventFieldsToJsonMapper();

            assertEquals(withMetadata.map(probeEvent()), withoutMetadata.map(probeEvent()));
        }
    }

    @Nested
    class ConformanceWithLegacyImplementation {

        @Test
        void everyRecordedEventMapsIdenticallyToLegacyAlgorithm() {
            EventFieldsToJsonMapper mapper = new EventFieldsToJsonMapper();
            mapper.update(recordedEventTypes);

            LegacyEventFieldsMapper legacy = new LegacyEventFieldsMapper();
            legacy.update(recordedEventTypes);

            for (RecordedEvent event : recordedEvents) {
                assertEquals(
                        legacy.map(event),
                        mapper.map(event),
                        () -> "Mismatch for event type: " + event.getEventType().getName());
            }
        }

        @Test
        void activeSettingEventsCarryIdAndLabel() {
            EventFieldsToJsonMapper mapper = new EventFieldsToJsonMapper();
            mapper.update(recordedEventTypes);

            List<RecordedEvent> activeSettings = recordedEvents.stream()
                    .filter(e -> ACTIVE_SETTING_EVENT_NAME.equals(e.getEventType().getName()))
                    .toList();
            assertFalse(activeSettings.isEmpty(), "Recording is expected to contain jdk.ActiveSetting events");

            for (RecordedEvent event : activeSettings) {
                ObjectNode node = mapper.map(event);
                assertTrue(node.has("id"));
                assertTrue(node.has("label"));
            }
        }
    }

    @Nested
    class ModuleStructFields {

        @Test
        void flattensModuleAndPackageStructsToTheirName() throws IOException {
            Path dumpFile = Files.createTempFile("module-events-mapper-test", ".jfr");
            List<RecordedEvent> moduleEvents;
            List<EventType> eventTypes;
            try (Recording recording = new Recording()) {
                recording.enable("jdk.ModuleRequire");
                recording.enable("jdk.ModuleExport");
                recording.start();
                recording.stop();
                recording.dump(dumpFile);
            }
            moduleEvents = RecordingFile.readAllEvents(dumpFile);
            eventTypes = moduleEvents.stream().map(RecordedEvent::getEventType).distinct().toList();
            Files.deleteIfExists(dumpFile);

            List<RecordedEvent> requires = moduleEvents.stream()
                    .filter(e -> "jdk.ModuleRequire".equals(e.getEventType().getName()))
                    .toList();
            // The module graph is dumped at chunk start; bail out gracefully if a JVM does not emit it.
            assumeFalse(requires.isEmpty(), "Recording is expected to contain jdk.ModuleRequire events");

            EventFieldsToJsonMapper mapper = new EventFieldsToJsonMapper();
            mapper.update(eventTypes);

            boolean sawJavaBase = false;
            for (RecordedEvent event : requires) {
                ObjectNode node = mapper.map(event);
                // requiredModule is a Module struct; it must flatten to a plain name, not a RecordedObject dump.
                if (node.hasNonNull("requiredModule")) {
                    String required = node.get("requiredModule").asString();
                    assertFalse(required.contains("{"), "Module struct must not be a toString() blob: " + required);
                    assertFalse(required.contains("="), "Module struct must not be a toString() blob: " + required);
                    sawJavaBase = sawJavaBase || "java.base".equals(required);
                }
            }
            assertTrue(sawJavaBase, "Every module requires java.base, so it should appear as a flattened name");
        }
    }

    /**
     * Verbatim copy of the original per-event implementation, used as the
     * behavioral reference for the conformance test above.
     */
    private static final class LegacyEventFieldsMapper {

        private static final String TIMESTAMP_TYPE_NAME = Timestamp.class.getTypeName();
        private static final String PERCENTAGE_TYPE_NAME = Percentage.class.getTypeName();
        private static final String TIMESPAN_TYPE_NAME = Timespan.class.getTypeName();

        private final Map<Long, EventType> eventTypes = new HashMap<>();

        void update(List<EventType> eventTypes) {
            eventTypes.forEach(e -> this.eventTypes.put(e.getId(), e));
        }

        ObjectNode map(RecordedEvent event) {
            ObjectNode node = Json.createObject();
            for (ValueDescriptor field : event.getFields()) {
                if (!EventFieldsToJsonMapper.IGNORED_FIELDS.contains(field.getName())) {
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
                    } else if ("jdk.ActiveSetting".equals(event.getEventType().getName())
                            && "id".equals(field.getName())) {
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
}
