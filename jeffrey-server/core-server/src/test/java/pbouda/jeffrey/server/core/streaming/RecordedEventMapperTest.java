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

package pbouda.jeffrey.server.core.streaming;

import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.Registered;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pbouda.jeffrey.server.api.v1.StreamingEvent;
import pbouda.jeffrey.server.api.v1.TypedValue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecordedEventMapperTest {

    private static final String SESSION_ID = "test-session-001";

    @Name("test.MapperTestEvent")
    @Registered(false)
    static class MapperTestEvent extends Event {
        boolean active;
        float ratio;
        double score;
    }

    @Name("test.VirtualThreadEvent")
    @Registered(false)
    static class VirtualThreadEvent extends Event {
        String marker;
    }

    @Nested
    class CpuLoadEvent {

        @Test
        void mapsEventTypeAndSessionId() throws IOException {
            RecordedEvent event = readFirstEvent("jdk.CPULoad");

            StreamingEvent mapped = RecordedEventMapper.toStreamingEvent(SESSION_ID, event);

            assertEquals("jdk.CPULoad", mapped.getEventType());
            assertEquals(SESSION_ID, mapped.getSessionId());
            assertTrue(mapped.getTimestamp() > 0);
        }

        @Test
        void mapsPercentageAnnotatedFields() throws IOException {
            RecordedEvent event = readFirstEvent("jdk.CPULoad");

            StreamingEvent mapped = RecordedEventMapper.toStreamingEvent(SESSION_ID, event);
            Map<String, TypedValue> fields = mapped.getFieldsMap();

            assertTrue(fields.containsKey("jvmUser"), "Should contain jvmUser field");
            assertTrue(fields.get("jvmUser").hasFloatValue(), "jvmUser should be a float (percentage)");

            assertTrue(fields.containsKey("jvmSystem"), "Should contain jvmSystem field");
            assertTrue(fields.containsKey("machineTotal"), "Should contain machineTotal field");
        }
    }

    @Nested
    class ExecutionSampleEvent {

        @Test
        void mapsThreadAsString() throws IOException {
            RecordedEvent event = readFirstEvent("jdk.ExecutionSample");

            StreamingEvent mapped = RecordedEventMapper.toStreamingEvent(SESSION_ID, event);
            Map<String, TypedValue> fields = mapped.getFieldsMap();

            assertTrue(fields.containsKey("sampledThread"), "Should contain sampledThread field");
            assertTrue(fields.get("sampledThread").hasStringValue(), "sampledThread should be a string");
            assertFalse(fields.get("sampledThread").getStringValue().isEmpty());
        }
    }

    @Nested
    class TimestampAnnotation {

        @Test
        void mapsValidTimestampToEpochMillis() throws IOException {
            // Use jdk.ThreadPark — its startTime field has @Timestamp and all other
            // fields have safe values (no "Forever" durations that overflow toNanos())
            RecordedEvent event = readFirstEvent("jdk.ThreadPark");

            StreamingEvent mapped = RecordedEventMapper.toStreamingEvent(SESSION_ID, event);
            Map<String, TypedValue> fields = mapped.getFieldsMap();

            assertTrue(fields.containsKey("startTime"), "Should contain startTime field");
            TypedValue startTime = fields.get("startTime");
            assertTrue(startTime.hasLongValue(), "startTime should be a long (epoch millis)");
            assertTrue(startTime.getLongValue() > 0, "startTime should be positive epoch millis");
        }

        @Test
        void mapsInstantMinToNull() throws IOException {
            RecordedEvent event = readFirstEvent("jdk.ThreadPark");

            StreamingEvent mapped = RecordedEventMapper.toStreamingEvent(SESSION_ID, event);
            Map<String, TypedValue> fields = mapped.getFieldsMap();

            // "until" is N/A (Instant.MIN) when timeout=0, so it should be absent
            assertFalse(fields.containsKey("until"),
                    "until=N/A (Instant.MIN) should be filtered out as null");
        }
    }

    @Nested
    class TimespanAnnotation {

        @Test
        void mapsPositiveDurationToNanos() throws IOException {
            RecordedEvent event = readFirstEvent("jdk.ThreadPark");

            StreamingEvent mapped = RecordedEventMapper.toStreamingEvent(SESSION_ID, event);
            Map<String, TypedValue> fields = mapped.getFieldsMap();

            assertTrue(fields.containsKey("duration"), "Should contain duration field");
            TypedValue duration = fields.get("duration");
            assertTrue(duration.hasLongValue(), "duration should be a long (nanos)");
            assertTrue(duration.getLongValue() > 0, "duration should be positive nanos");
        }

        @Test
        void mapsZeroDurationToNull() throws IOException {
            RecordedEvent event = readFirstEvent("jdk.ThreadPark");

            StreamingEvent mapped = RecordedEventMapper.toStreamingEvent(SESSION_ID, event);
            Map<String, TypedValue> fields = mapped.getFieldsMap();

            // timeout=0s means Duration.ZERO, which should map to null (absent)
            assertFalse(fields.containsKey("timeout"),
                    "timeout=0s (Duration.ZERO) should be filtered out as null");
        }
    }

    @Nested
    class ClassType {

        @Test
        void mapsRecordedClassToString() throws IOException {
            RecordedEvent event = readFirstEvent("jdk.ThreadPark");

            StreamingEvent mapped = RecordedEventMapper.toStreamingEvent(SESSION_ID, event);
            Map<String, TypedValue> fields = mapped.getFieldsMap();

            assertTrue(fields.containsKey("parkedClass"), "Should contain parkedClass field");
            TypedValue parkedClass = fields.get("parkedClass");
            assertTrue(parkedClass.hasStringValue(), "parkedClass should be a string");
            assertFalse(parkedClass.getStringValue().isEmpty());
        }
    }

    @Nested
    class LongAndIntTypes {

        @Test
        void mapsLongField() throws IOException {
            RecordedEvent event = readFirstEvent("jdk.ObjectAllocationInNewTLAB");

            StreamingEvent mapped = RecordedEventMapper.toStreamingEvent(SESSION_ID, event);
            Map<String, TypedValue> fields = mapped.getFieldsMap();

            assertTrue(fields.containsKey("allocationSize"), "Should contain allocationSize field");
            TypedValue allocationSize = fields.get("allocationSize");
            assertTrue(allocationSize.hasLongValue(), "allocationSize should be a long");
            assertTrue(allocationSize.getLongValue() > 0);
        }

        @Test
        void mapsIntFieldAsLong() throws IOException {
            RecordedEvent event = readFirstEvent("jdk.GCHeapSummary");

            StreamingEvent mapped = RecordedEventMapper.toStreamingEvent(SESSION_ID, event);
            Map<String, TypedValue> fields = mapped.getFieldsMap();

            assertTrue(fields.containsKey("gcId"), "Should contain gcId field");
            TypedValue gcId = fields.get("gcId");
            assertTrue(gcId.hasLongValue(), "gcId (int) should be mapped as long");
            assertEquals(16, gcId.getLongValue());
        }
    }

    @Nested
    class ToStringFallback {

        @Test
        void mapsStringFieldViaToString() throws IOException {
            RecordedEvent event = readFirstEvent("jdk.ActiveSetting");

            StreamingEvent mapped = RecordedEventMapper.toStreamingEvent(SESSION_ID, event);
            Map<String, TypedValue> fields = mapped.getFieldsMap();

            assertTrue(fields.containsKey("name"), "Should contain name field");
            TypedValue name = fields.get("name");
            assertTrue(name.hasStringValue(), "name should be a string");
            assertFalse(name.getStringValue().isEmpty());
        }

        @Test
        void mapsGcWhenFieldViaToString() throws IOException {
            RecordedEvent event = readFirstEvent("jdk.GCHeapSummary");

            StreamingEvent mapped = RecordedEventMapper.toStreamingEvent(SESSION_ID, event);
            Map<String, TypedValue> fields = mapped.getFieldsMap();

            assertTrue(fields.containsKey("when"), "Should contain when field");
            TypedValue when = fields.get("when");
            assertTrue(when.hasStringValue(), "when should be a string");
            assertEquals("After GC", when.getStringValue());
        }
    }

    @Nested
    class CustomEventPrimitives {

        @Test
        void mapsBooleanField(@TempDir Path tempDir) throws Exception {
            RecordedEvent event = recordAndReadCustomEvent(tempDir);

            StreamingEvent mapped = RecordedEventMapper.toStreamingEvent(SESSION_ID, event);
            Map<String, TypedValue> fields = mapped.getFieldsMap();

            assertTrue(fields.containsKey("active"), "Should contain active field");
            assertTrue(fields.get("active").hasBoolValue(), "active should be a bool");
            assertTrue(fields.get("active").getBoolValue());
        }

        @Test
        void mapsFloatWithoutAnnotationAsDouble(@TempDir Path tempDir) throws Exception {
            RecordedEvent event = recordAndReadCustomEvent(tempDir);

            StreamingEvent mapped = RecordedEventMapper.toStreamingEvent(SESSION_ID, event);
            Map<String, TypedValue> fields = mapped.getFieldsMap();

            assertTrue(fields.containsKey("ratio"), "Should contain ratio field");
            TypedValue ratio = fields.get("ratio");
            assertTrue(ratio.hasDoubleValue(), "float without @Percentage falls into float/double primitive branch");
            assertEquals(0.75, ratio.getDoubleValue(), 0.01);
        }

        @Test
        void mapsDoubleField(@TempDir Path tempDir) throws Exception {
            RecordedEvent event = recordAndReadCustomEvent(tempDir);

            StreamingEvent mapped = RecordedEventMapper.toStreamingEvent(SESSION_ID, event);
            Map<String, TypedValue> fields = mapped.getFieldsMap();

            assertTrue(fields.containsKey("score"), "Should contain score field");
            TypedValue score = fields.get("score");
            assertTrue(score.hasDoubleValue(), "score should be a double");
            assertEquals(3.14, score.getDoubleValue(), 0.01);
        }

        private RecordedEvent recordAndReadCustomEvent(Path tempDir) throws Exception {
            Path dumpFile = tempDir.resolve("custom-event.jfr");
            FlightRecorder.register(MapperTestEvent.class);

            try (Recording recording = new Recording()) {
                recording.enable("test.MapperTestEvent");
                recording.start();

                MapperTestEvent event = new MapperTestEvent();
                event.active = true;
                event.ratio = 0.75f;
                event.score = 3.14;
                event.commit();

                recording.stop();
                recording.dump(dumpFile);
            }

            return readFirstEventFromFile(dumpFile, "test.MapperTestEvent");
        }
    }

    @Nested
    class VirtualThreadMapping {

        @Test
        void appendsVirtualSuffix(@TempDir Path tempDir) throws Exception {
            Path dumpFile = tempDir.resolve("virtual-thread-event.jfr");
            FlightRecorder.register(VirtualThreadEvent.class);

            try (Recording recording = new Recording()) {
                recording.enable("test.VirtualThreadEvent");
                recording.start();

                Thread vthread = Thread.ofVirtual().name("my-vthread").start(() -> {
                    VirtualThreadEvent event = new VirtualThreadEvent();
                    event.marker = "test";
                    event.commit();
                });
                vthread.join();

                recording.stop();
                recording.dump(dumpFile);
            }

            RecordedEvent event = readFirstEventFromFile(dumpFile, "test.VirtualThreadEvent");
            StreamingEvent mapped = RecordedEventMapper.toStreamingEvent(SESSION_ID, event);
            Map<String, TypedValue> fields = mapped.getFieldsMap();

            assertTrue(fields.containsKey("eventThread"), "Should contain eventThread field");
            TypedValue eventThread = fields.get("eventThread");
            assertTrue(eventThread.hasStringValue());
            assertTrue(eventThread.getStringValue().endsWith(" (Virtual)"),
                    "Virtual thread name should end with ' (Virtual)': " + eventThread.getStringValue());
        }
    }

    @Nested
    class MethodType {

        @Test
        @Disabled("Requires JFR recording with jdk.MethodTrace events which need special JVM settings")
        void mapsMethodAsTypeHashName() {
            // jdk.types.Method branch: method.getType().getName() + "#" + method.getName()
            // Cannot be tested without a recording containing jdk.MethodTrace events
        }
    }

    @Nested
    class FieldFiltering {

        @Test
        void skipsStackTraceField() throws IOException {
            RecordedEvent event = readFirstEvent("jdk.ExecutionSample");

            StreamingEvent mapped = RecordedEventMapper.toStreamingEvent(SESSION_ID, event);

            assertFalse(mapped.getFieldsMap().containsKey("stackTrace"),
                    "stackTrace field should be filtered out");
        }

        @Test
        void noNullValuesInFieldsMap() throws IOException {
            RecordedEvent event = readFirstEvent("jdk.CPULoad");

            StreamingEvent mapped = RecordedEventMapper.toStreamingEvent(SESSION_ID, event);

            for (Map.Entry<String, TypedValue> entry : mapped.getFieldsMap().entrySet()) {
                assertNotNull(entry.getValue(), "Field " + entry.getKey() + " should not be null");
            }
        }
    }

    private static RecordedEvent readFirstEvent(String eventType) throws IOException {
        List<RecordedEvent> events = new ArrayList<>();
        try (EventStream stream = EventStream.openFile(JfrTestFiles.resolve(JfrTestFiles.PROFILE_1))) {
            stream.onEvent(eventType, events::add);
            stream.start();
        }
        assertFalse(events.isEmpty(), "Expected at least one " + eventType + " event in test file");
        return events.getFirst();
    }

    private static RecordedEvent readFirstEventFromFile(Path file, String eventType) throws IOException {
        List<RecordedEvent> events = new ArrayList<>();
        try (EventStream stream = EventStream.openFile(file)) {
            stream.onEvent(eventType, events::add);
            stream.start();
        }
        assertFalse(events.isEmpty(), "Expected at least one " + eventType + " event in " + file);
        return events.getFirst();
    }
}
