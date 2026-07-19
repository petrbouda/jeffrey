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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.otlpparser.OtlpProfileWriter.ExportFrame;
import cafe.jeffrey.otlpparser.OtlpProfileWriter.ExportSample;
import cafe.jeffrey.otlpparser.OtlpProfileWriter.SampleValueType;
import cafe.jeffrey.provider.profile.api.Event;
import cafe.jeffrey.provider.profile.api.EventFrame;
import cafe.jeffrey.provider.profile.api.EventStacktrace;
import cafe.jeffrey.shared.common.model.EventTypeName;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trips Jeffrey stacks through {@link OtlpProfileWriter} and back through {@link OtlpProfileReader},
 * asserting the frames, JVM class/method names, the {@code profile.frame.type} attribute, and — crucially —
 * the per-observation timestamps survive (so the timeseries reconstructs instead of collapsing to one instant).
 */
class OtlpProfileWriterTest {

    private static final long TIME_NANOS = 1_752_000_000_000_000_000L;
    private static final long DURATION_NANOS = 5_000_000_000L;
    private static final long ONE_SECOND_NANOS = 1_000_000_000L;

    private static final SampleValueType SAMPLES = new SampleValueType("samples", "count");

    // root-first frames: native root, JVM leaf
    private static final ExportFrame NATIVE_ROOT = new ExportFrame("", "__libc_start_main", 0, "Native");
    private static final ExportFrame JVM_LEAF = new ExportFrame("com.example.Foo", "doWork", 42, "JIT compiled");

    @TempDir
    Path tempDir;

    @Test
    void roundTripsFramesFrameTypesAndPerObservationTimestamps() {
        // One stack observed in two different one-second buckets: 3 samples at T, 7 samples at T+1s.
        long[] timestamps = {TIME_NANOS, TIME_NANOS + ONE_SECOND_NANOS};
        long[] values = {3, 7};
        List<ExportSample> samples = List.of(
                new ExportSample(List.of(NATIVE_ROOT, JVM_LEAF), timestamps, values));

        byte[] otlp = new OtlpProfileWriter()
                .write(SAMPLES, samples, TIME_NANOS, DURATION_NANOS, "checkout-service");

        Path file = tempDir.resolve("export.otlp");
        OtlpTestFiles.writeRaw(file, parse(otlp));

        RecordingEventWriterStub stub = new RecordingEventWriterStub();
        new OtlpProfileReader(stub).read(file);

        assertTrue(stub.eventTypes.stream().anyMatch(t -> t.name().equals(EventTypeName.OTEL_SAMPLES)),
                "expected an otel.samples event type: " + stub.eventTypes);

        // One event per observation, each at its own timestamp — the timing is preserved, not collapsed.
        List<Event> events = stub.events.stream()
                .filter(e -> EventTypeName.OTEL_SAMPLES.equals(e.eventType()))
                .toList();
        assertEquals(2, events.size(), "expected one event per observation bucket");
        assertEquals(2, events.stream().map(Event::startTimestamp).distinct().count(),
                "the two observations must keep distinct timestamps");
        assertEquals(Instant.ofEpochSecond(0, TIME_NANOS), events.get(0).startTimestamp());
        assertEquals(Instant.ofEpochSecond(0, TIME_NANOS + ONE_SECOND_NANOS), events.get(1).startTimestamp());

        // Count dimension: per-observation values become per-event samples; total is exact.
        assertEquals(10, events.stream().mapToLong(Event::samples).sum());

        // The JVM leaf frame round-trips class / method / frame type / line exactly.
        EventStacktrace stacktrace = stub.stacktracesById.values().iterator().next();
        assertEquals(2, stacktrace.frames().size());
        EventFrame leaf = stacktrace.frames().getLast();
        assertEquals("com.example.Foo", leaf.clazz());
        assertEquals("doWork", leaf.method());
        assertEquals("JIT compiled", leaf.type());
        assertEquals(42, leaf.line());

        // The native root keeps its native frame type via the profile.frame.type attribute.
        EventFrame root = stacktrace.frames().getFirst();
        assertEquals("Native", root.type());
        assertTrue(root.method().contains("__libc_start_main"), "native symbol should survive: " + root.method());
    }

    private static io.opentelemetry.proto.profiles.v1development.ProfilesData parse(byte[] bytes) {
        try {
            return io.opentelemetry.proto.profiles.v1development.ProfilesData.parseFrom(bytes);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }
}
