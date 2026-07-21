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

package cafe.jeffrey.pprofparser;

import com.google.perftools.profiles.ProfileProto.Profile;
import cafe.jeffrey.provider.profile.api.Event;
import cafe.jeffrey.provider.profile.api.EventFrame;
import cafe.jeffrey.provider.profile.api.EventStacktrace;
import cafe.jeffrey.provider.profile.api.EventThread;
import cafe.jeffrey.provider.profile.api.EventType;
import cafe.jeffrey.shared.common.model.StacktraceType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PprofProfileReaderTest {

    @Test
    void emitsOneEventTypePerSampleDimensionAndOneEventPerNonZeroValue() {
        PprofTestFixtures fixtures = new PprofTestFixtures()
                .sampleType("samples", "count")
                .sampleType("cpu", "nanoseconds")
                .time(1_000_000_000L, 5_000_000_000L);

        long main = fixtures.location("main.main", 10);
        long work = fixtures.location("main.doWork", 20);
        // leaf-first: doWork is the leaf, main the root
        fixtures.sample(List.of(work, main), List.of(3L, 7_000_000L));

        RecordingEventWriterStub writer = new RecordingEventWriterStub();
        new PprofProfileReader(writer).read(fixtures.build());

        assertEquals(1, writer.threadStarts);
        assertEquals(1, writer.threadCompletions);

        // one event type per dimension
        List<String> typeNames = writer.eventTypes.stream().map(EventType::name).toList();
        assertTrue(typeNames.contains("samples"), typeNames.toString());
        assertTrue(typeNames.contains("cpu"), typeNames.toString());

        // one event per non-zero dimension value
        assertEquals(2, writer.events.size());

        Event samplesEvent = eventOfType(writer, "samples");
        assertEquals(3, samplesEvent.samples());
        assertNull(samplesEvent.weight());

        Event cpuEvent = eventOfType(writer, "cpu");
        assertEquals(1, cpuEvent.samples());
        assertEquals(7_000_000L, cpuEvent.weight());

        // all events stamped with the profile collection time (pprof has no per-sample time)
        Instant expectedTime = Instant.ofEpochSecond(0, 1_000_000_000L);
        assertEquals(expectedTime, samplesEvent.startTimestamp());
        assertEquals(expectedTime, cpuEvent.startTimestamp());
    }

    @Test
    void mapsStackRootFirstAndDeduplicatesSharedStacks() {
        PprofTestFixtures fixtures = new PprofTestFixtures()
                .sampleType("cpu", "nanoseconds");
        long main = fixtures.location("main.main", 1);
        long work = fixtures.location("main.doWork", 2);
        fixtures.sample(List.of(work, main), List.of(100L));
        fixtures.sample(List.of(work, main), List.of(200L));

        RecordingEventWriterStub writer = new RecordingEventWriterStub();
        new PprofProfileReader(writer).read(fixtures.build());

        // two samples share the identical stack -> announced once
        assertEquals(1, writer.stacktracesById.size());
        EventStacktrace stacktrace = writer.stacktracesById.values().iterator().next();
        assertEquals(StacktraceType.NATIVE, stacktrace.type());

        List<EventFrame> frames = stacktrace.frames();
        // root-first: main.main is the root, main.doWork the leaf
        assertEquals("main", frames.get(0).clazz());
        assertEquals("main", frames.get(0).method());
        assertEquals("main", frames.get(1).clazz());
        assertEquals("doWork", frames.get(1).method());

        // both events reference the same deduplicated stacktrace id
        Long stacktraceId = writer.stacktracesById.keySet().iterator().next();
        assertTrue(writer.events.stream().allMatch(e -> stacktraceId.equals(e.stacktraceId())));
    }

    @Test
    void skipsZeroValuedDimensions() {
        PprofTestFixtures fixtures = new PprofTestFixtures()
                .sampleType("alloc_objects", "count")
                .sampleType("alloc_space", "bytes");
        long alloc = fixtures.location("main.allocate", 1);
        // only alloc_space is non-zero
        fixtures.sample(List.of(alloc), List.of(0L, 4096L));

        RecordingEventWriterStub writer = new RecordingEventWriterStub();
        new PprofProfileReader(writer).read(fixtures.build());

        assertEquals(1, writer.events.size());
        Event event = writer.events.get(0);
        assertEquals("alloc_space", event.eventType());
        assertEquals(4096L, event.weight());
        assertEquals(1, event.samples());
    }

    @Test
    void recoversThreadFromLabelsOtherwiseUsesSyntheticThread() {
        PprofTestFixtures withThread = new PprofTestFixtures()
                .sampleType("cpu", "nanoseconds");
        long loc = withThread.location("main.main", 1);
        withThread.sample(
                List.of(loc),
                List.of(10L),
                List.of(withThread.numberLabel("tid", 42), withThread.stringLabel("thread name", "worker-1")));

        RecordingEventWriterStub writer = new RecordingEventWriterStub();
        new PprofProfileReader(writer).read(withThread.build());

        assertEquals(1, writer.threadsById.size());
        EventThread thread = writer.threadsById.values().iterator().next();
        assertEquals("worker-1", thread.name());
        assertEquals(42L, thread.osId());

        // labels are flattened into the event fields
        Event event = writer.events.get(0);
        assertEquals(42L, event.fields().get("tid").asLong());
        assertEquals("worker-1", event.fields().get("thread name").asText());
    }

    @Test
    void usesSyntheticThreadWhenNoLabelsPresent() {
        PprofTestFixtures fixtures = new PprofTestFixtures()
                .sampleType("cpu", "nanoseconds");
        long loc = fixtures.location("main.main", 1);
        fixtures.sample(List.of(loc), List.of(10L));

        RecordingEventWriterStub writer = new RecordingEventWriterStub();
        new PprofProfileReader(writer).read(fixtures.build());

        assertEquals(1, writer.threadsById.size());
        EventThread thread = writer.threadsById.values().iterator().next();
        assertEquals("pprof-samples", thread.name());
    }

    private static Event eventOfType(RecordingEventWriterStub writer, String type) {
        return writer.events.stream()
                .filter(e -> e.eventType().equals(type))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No event of type " + type));
    }

    @Test
    void readsGzipCompressedProfileFromDisk() throws Exception {
        PprofTestFixtures fixtures = new PprofTestFixtures()
                .sampleType("cpu", "nanoseconds")
                .time(2_000_000_000L, 1_000_000_000L);
        long loc = fixtures.location("main.main", 1);
        fixtures.sample(List.of(loc), List.of(50L));
        Profile profile = fixtures.build();

        java.nio.file.Path file = java.nio.file.Files.createTempFile("fixture", ".pb.gz");
        try (java.io.OutputStream out = new java.util.zip.GZIPOutputStream(java.nio.file.Files.newOutputStream(file))) {
            profile.writeTo(out);
        }

        Profile roundTripped = new PprofStreamReader().read(file);
        assertEquals(1, roundTripped.getSampleCount());
        java.nio.file.Files.delete(file);
    }
}
