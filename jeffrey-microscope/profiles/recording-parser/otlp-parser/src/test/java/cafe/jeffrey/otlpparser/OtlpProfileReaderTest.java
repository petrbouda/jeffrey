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

package cafe.jeffrey.otlpparser;

import io.opentelemetry.proto.profiles.v1development.ProfilesData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.otlpparser.mapping.OtelSemconv;
import cafe.jeffrey.provider.profile.api.Event;
import cafe.jeffrey.provider.profile.api.EventStacktrace;
import cafe.jeffrey.provider.profile.api.EventType;
import cafe.jeffrey.shared.common.model.StacktraceType;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtlpProfileReaderTest {

    private static final long BASE_TIME_NANOS = 1_752_000_000_000_000_000L;

    @TempDir
    Path tempDir;

    private RecordingEventWriterStub writer;

    @BeforeEach
    void setUp() {
        writer = new RecordingEventWriterStub();
    }

    private Path writeRecording(ProfilesData... frames) {
        Path file = tempDir.resolve("recording.otlp");
        OtlpTestFiles.writeFramed(file, List.of(frames));
        return file;
    }

    /**
     * Fixture: a cpu/nanoseconds profile with one sample referencing a stack of a JVM frame on top
     * of a native frame, thread attributes and per-timestamp values.
     */
    private ProfilesData cpuFrame() {
        OtlpTestFixtures fixtures = new OtlpTestFixtures();
        fixtures.resourceAttribute(OtelSemconv.SERVICE_NAME, "checkout-service");

        int jvmFrameType = fixtures.stringAttribute(OtelSemconv.PROFILE_FRAME_TYPE, "jvm");
        int nativeFrameType = fixtures.stringAttribute(OtelSemconv.PROFILE_FRAME_TYPE, "native");

        int libc = fixtures.mapping("/usr/lib/libc.so.6");
        int jvmFunction = fixtures.function("com.example.Foo.doWork");
        int nativeFunction = fixtures.function("__libc_start_main");

        int jvmLocation = fixtures.location(0, jvmFunction, 42, jvmFrameType);
        int nativeLocation = fixtures.location(libc, nativeFunction, 0, nativeFrameType);

        // leaf-first: JVM frame is the leaf, native frame is the root
        int stack = fixtures.stack(List.of(jvmLocation, nativeLocation));

        int threadName = fixtures.stringAttribute(OtelSemconv.THREAD_NAME, "worker-1");
        int threadId = fixtures.longAttribute(OtelSemconv.THREAD_ID, 77);

        fixtures.profile(fixtures.profileBuilder("cpu", "nanoseconds", BASE_TIME_NANOS)
                .addSamples(fixtures.sampleBuilder(stack)
                        .addAttributeIndices(threadName)
                        .addAttributeIndices(threadId)
                        .addValues(10_000_000)
                        .addValues(10_000_000)
                        .addTimestampsUnixNano(BASE_TIME_NANOS)
                        .addTimestampsUnixNano(BASE_TIME_NANOS + 10_000_000))
                .build());
        return fixtures.build();
    }

    @Nested
    class CpuProfile {

        @Test
        void emitsOneEventPerTimestamp() {
            new OtlpProfileReader(writer).read(writeRecording(cpuFrame()));

            assertEquals(2, writer.events.size());
            Event first = writer.events.getFirst();
            assertEquals("cpu", first.eventType());
            assertEquals(Instant.ofEpochSecond(0, BASE_TIME_NANOS), first.startTimestamp());
            assertEquals(1, first.samples());
            assertEquals(10_000_000L, first.weight());
            assertNull(first.duration());
        }

        @Test
        void followsAnnounceOnceProtocol() {
            new OtlpProfileReader(writer).read(writeRecording(cpuFrame()));

            assertEquals(1, writer.threadStarts);
            assertEquals(1, writer.threadCompletions);
            assertEquals(1, writer.threadsById.size());
            assertEquals(1, writer.stacktracesById.size());

            // both events reference the single announced thread/stacktrace
            Event first = writer.events.get(0);
            Event second = writer.events.get(1);
            assertEquals(first.threadId(), second.threadId());
            assertEquals(first.stacktraceId(), second.stacktraceId());
        }

        @Test
        void mapsThreadFromSampleAttributes() {
            new OtlpProfileReader(writer).read(writeRecording(cpuFrame()));

            var thread = writer.threadsById.values().iterator().next();
            assertEquals("worker-1", thread.name());
            assertEquals(77L, thread.osId());
        }

        @Test
        void reversesStackToRootFirstAndMapsFrameTypes() {
            new OtlpProfileReader(writer).read(writeRecording(cpuFrame()));

            EventStacktrace stacktrace = writer.stacktracesById.values().iterator().next();
            assertEquals(StacktraceType.APPLICATION, stacktrace.type());
            assertEquals(2, stacktrace.frames().size());

            // root-first: the native root frame first, the JVM leaf frame last
            var rootFrame = stacktrace.frames().getFirst();
            assertEquals("libc.so.6", rootFrame.clazz());
            assertEquals("__libc_start_main", rootFrame.method());
            assertEquals("Native", rootFrame.type());

            var leafFrame = stacktrace.frames().getLast();
            assertEquals("com.example.Foo", leafFrame.clazz());
            assertEquals("doWork", leafFrame.method());
            assertEquals("JIT compiled", leafFrame.type());
            assertEquals(42, leafFrame.line());
        }

        @Test
        void emitsEventTypeWithSettings() {
            new OtlpProfileReader(writer).read(writeRecording(cpuFrame()));

            assertEquals(1, writer.eventTypes.size());
            EventType eventType = writer.eventTypes.getFirst();
            assertEquals("cpu", eventType.name());

            boolean serviceNameSetting = writer.settings.stream()
                    .anyMatch(s -> s.eventType().equals("cpu")
                            && s.name().equals(OtelSemconv.SERVICE_NAME)
                            && s.value().equals("checkout-service"));
            assertTrue(serviceNameSetting);
        }
    }

    @Nested
    class SampleShapes {

        private ProfilesData shapeFrame(List<Long> values, List<Long> timestamps) {
            OtlpTestFixtures fixtures = new OtlpTestFixtures();
            int function = fixtures.function("com.example.Shape.run");
            int location = fixtures.location(0, function, 1, 0);
            int stack = fixtures.stack(List.of(location));

            var sample = fixtures.sampleBuilder(stack);
            values.forEach(sample::addValues);
            timestamps.forEach(sample::addTimestampsUnixNano);

            fixtures.profile(fixtures.profileBuilder("samples", "count", BASE_TIME_NANOS)
                    .addSamples(sample)
                    .build());
            return fixtures.build();
        }

        @Test
        void aggregateValueWithoutTimestampsBecomesSingleEvent() {
            new OtlpProfileReader(writer).read(writeRecording(shapeFrame(List.of(25L), List.of())));

            assertEquals(1, writer.events.size());
            Event event = writer.events.getFirst();
            assertEquals(25, event.samples());
            assertNull(event.weight());
            assertEquals(Instant.ofEpochSecond(0, BASE_TIME_NANOS), event.startTimestamp());
        }

        @Test
        void timestampsOnlyShapeCountsOnePerTimestamp() {
            new OtlpProfileReader(writer).read(writeRecording(
                    shapeFrame(List.of(), List.of(BASE_TIME_NANOS, BASE_TIME_NANOS + 1, BASE_TIME_NANOS + 2))));

            assertEquals(3, writer.events.size());
            assertTrue(writer.events.stream().allMatch(e -> e.samples() == 1));
        }

        @Test
        void cardinalityMismatchSpreadsTotalEvenly() {
            new OtlpProfileReader(writer).read(writeRecording(
                    shapeFrame(List.of(10L), List.of(BASE_TIME_NANOS, BASE_TIME_NANOS + 1, BASE_TIME_NANOS + 2))));

            assertEquals(3, writer.events.size());
            long total = writer.events.stream().mapToLong(Event::samples).sum();
            assertEquals(10, total);
        }
    }

    @Nested
    class AllocationProfile {

        @Test
        void bytesUnitMapsToWeightWithEntity() {
            OtlpTestFixtures fixtures = new OtlpTestFixtures();
            int function = fixtures.function("com.example.Alloc.allocate");
            int jvmFrameType = fixtures.stringAttribute(OtelSemconv.PROFILE_FRAME_TYPE, "jvm");
            int location = fixtures.location(0, function, 10, jvmFrameType);
            int stack = fixtures.stack(List.of(location));
            int classAttr = fixtures.stringAttribute("class", "byte[]");

            fixtures.profile(fixtures.profileBuilder("alloc", "bytes", BASE_TIME_NANOS)
                    .addSamples(fixtures.sampleBuilder(stack)
                            .addAttributeIndices(classAttr)
                            .addValues(4096)
                            .addTimestampsUnixNano(BASE_TIME_NANOS))
                    .build());

            new OtlpProfileReader(writer).read(writeRecording(fixtures.build()));

            Event event = writer.events.getFirst();
            assertEquals("alloc", event.eventType());
            assertEquals(1, event.samples());
            assertEquals(4096L, event.weight());
            assertEquals("byte[]", event.weightEntity());
            assertEquals("byte[]", event.fields().get("class").asString());
        }
    }

    @Nested
    class MultipleFrames {

        @Test
        void foldsAllFramesIntoTheSameEventTypes() {
            new OtlpProfileReader(writer).read(writeRecording(cpuFrame(), cpuFrame()));

            assertEquals(4, writer.events.size());
            assertEquals(1, writer.eventTypes.size());
            // thread dedup is content-based and survives across frames
            assertEquals(1, writer.threadsById.size());
        }
    }
}
