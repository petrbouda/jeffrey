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

package cafe.jeffrey.profile.parser;

import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Name;
import jdk.jfr.Registered;
import jdk.jfr.Recording;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WeightExtractor factory methods")
class WeightExtractorTest {

    private static final String ENTITY_EVENT_NAME = "test.WeightEntityEvent";

    @Name(ENTITY_EVENT_NAME)
    @Registered(false)
    static class WeightEntityEvent extends Event {
        Class<?> monitorClass;
        long size;
    }

    @Nested
    @DisplayName("duration() no-arg factory method")
    class DurationFormatterProducesTimeString {

        @Test
        @DisplayName("100_000_000 nanos (100ms) formats as a time string containing 'ms'")
        void hundredMillisecondsContainsMs() {
            WeightExtractor extractor = WeightExtractor.duration();
            LongFunction<String> formatter = extractor.formatter();

            String result = formatter.apply(100_000_000L);

            assertTrue(result.contains("ms") || result.contains("100"),
                    "Expected time string for 100ms, but got: " + result);
            assertFalse(result.contains("MB") || result.contains("KB") || result.contains("GB") || result.contains("iB"),
                    "Duration formatter should not produce byte units, but got: " + result);
        }

        @Test
        @DisplayName("1_000_000 nanos (1ms) formats as a time string containing 'ms'")
        void oneMillisecondContainsMs() {
            WeightExtractor extractor = WeightExtractor.duration();
            LongFunction<String> formatter = extractor.formatter();

            String result = formatter.apply(1_000_000L);

            assertTrue(result.contains("ms") || result.contains("1"),
                    "Expected time string for 1ms, but got: " + result);
            assertFalse(result.contains("MB") || result.contains("KB") || result.contains("GB") || result.contains("iB"),
                    "Duration formatter should not produce byte units, but got: " + result);
        }

        @Test
        @DisplayName("1_000 nanos (1us) does not contain byte units")
        void oneMicrosecondDoesNotContainByteUnits() {
            WeightExtractor extractor = WeightExtractor.duration();
            LongFunction<String> formatter = extractor.formatter();

            String result = formatter.apply(1_000L);

            assertFalse(result.contains("B") || result.contains("KB"),
                    "Duration formatter should not produce byte units for 1us, but got: " + result);
            assertTrue(result.contains("s") || result.contains("ns"),
                    "Expected time string for 1us, but got: " + result);
        }
    }

    @Nested
    @DisplayName("duration(entityClassField) factory method")
    class DurationWithEntityClassFieldFormatterProducesTimeString {

        @Test
        @DisplayName("Formatter from duration(entityClassField) also uses DurationUtils")
        void durationWithEntityFieldUsesDurationFormatter() {
            WeightExtractor extractor = WeightExtractor.duration("someField");
            LongFunction<String> formatter = extractor.formatter();

            String result = formatter.apply(100_000_000L);

            assertTrue(result.contains("ms") || result.contains("100"),
                    "Expected time string for 100ms, but got: " + result);
            assertFalse(result.contains("MB") || result.contains("KB") || result.contains("GB") || result.contains("iB"),
                    "Duration formatter should not produce byte units, but got: " + result);
        }

        @Test
        @DisplayName("1_000 nanos via duration(entityClassField) does not contain byte units")
        void durationWithEntityFieldOneMicrosecond() {
            WeightExtractor extractor = WeightExtractor.duration("someField");
            LongFunction<String> formatter = extractor.formatter();

            String result = formatter.apply(1_000L);

            assertFalse(result.contains("B") || result.contains("KB"),
                    "Duration formatter should not produce byte units for 1us, but got: " + result);
            assertTrue(result.contains("s") || result.contains("ns"),
                    "Expected time string for 1us, but got: " + result);
        }
    }

    @Nested
    @DisplayName("Class-typed entity field that the event left empty")
    class MissingEntityClass {

        @Test
        @DisplayName("duration(entityClassField) yields no entity instead of failing")
        void durationYieldsNoEntity(@TempDir Path tempDir) throws Exception {
            RecordedEvent event = recordEntityEvent(tempDir, null);

            WeightExtractor extractor = WeightExtractor.duration("monitorClass");

            assertNull(extractor.entityExtractor().apply(event));
        }

        @Test
        @DisplayName("allocation(fieldName, entityClassField) yields no entity instead of failing")
        void allocationYieldsNoEntity(@TempDir Path tempDir) throws Exception {
            RecordedEvent event = recordEntityEvent(tempDir, null);

            WeightExtractor extractor = WeightExtractor.allocation("size", "monitorClass");

            assertNull(extractor.entityExtractor().apply(event));
            assertEquals(1024L, extractor.extractor().applyAsLong(event));
        }
    }

    @Nested
    @DisplayName("Class-typed entity field that the event populated")
    class PresentEntityClass {

        @Test
        @DisplayName("duration(entityClassField) reads the class name")
        void durationReadsClassName(@TempDir Path tempDir) throws Exception {
            RecordedEvent event = recordEntityEvent(tempDir, String.class);

            WeightExtractor extractor = WeightExtractor.duration("monitorClass");

            assertEquals("java.lang.String", extractor.entityExtractor().apply(event));
        }

        @Test
        @DisplayName("allocation(fieldName, entityClassField) reads the class name")
        void allocationReadsClassName(@TempDir Path tempDir) throws Exception {
            RecordedEvent event = recordEntityEvent(tempDir, String.class);

            WeightExtractor extractor = WeightExtractor.allocation("size", "monitorClass");

            assertEquals("java.lang.String", extractor.entityExtractor().apply(event));
        }
    }

    private static RecordedEvent recordEntityEvent(Path tempDir, Class<?> monitorClass) throws IOException {
        Path dumpFile = tempDir.resolve("weight-entity.jfr");
        FlightRecorder.register(WeightEntityEvent.class);

        try (Recording recording = new Recording()) {
            recording.enable(ENTITY_EVENT_NAME);
            recording.start();

            WeightEntityEvent event = new WeightEntityEvent();
            event.begin();
            event.monitorClass = monitorClass;
            event.size = 1024L;
            event.commit();

            recording.stop();
            recording.dump(dumpFile);
        }

        List<RecordedEvent> events = new ArrayList<>();
        try (EventStream stream = EventStream.openFile(dumpFile)) {
            stream.onEvent(ENTITY_EVENT_NAME, events::add);
            stream.start();
        }
        assertFalse(events.isEmpty(), "Expected at least one " + ENTITY_EVENT_NAME + " event");
        return events.getFirst();
    }
}
