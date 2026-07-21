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
import org.junit.jupiter.api.Test;
import cafe.jeffrey.pprofparser.PprofProfileWriter.ExportFrame;
import cafe.jeffrey.pprofparser.PprofProfileWriter.ExportSample;
import cafe.jeffrey.pprofparser.PprofProfileWriter.SampleValueType;
import cafe.jeffrey.provider.profile.api.Event;
import cafe.jeffrey.provider.profile.api.EventFrame;
import cafe.jeffrey.provider.profile.api.EventStacktrace;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trips Jeffrey stacks through {@link PprofProfileWriter} and back through the existing
 * {@link PprofProfileReader}, asserting the frames, class/method names and sample totals survive,
 * plus the pprof invariants (gzip on disk, {@code string_table[0] == ""}).
 */
class PprofProfileWriterTest {

    private static final long TIME_NANOS = 1_752_000_000_000_000_000L;
    private static final long DURATION_NANOS = 5_000_000_000L;

    private static final SampleValueType SAMPLES = new SampleValueType("samples", "count");
    private static final SampleValueType CPU = new SampleValueType("cpu", "nanoseconds");

    // root-first frames
    private static final ExportFrame NATIVE_ROOT = new ExportFrame("", "Thread::call_run", 0);
    private static final ExportFrame SERVICE = new ExportFrame("com/example/Service", "process", 42);
    private static final ExportFrame REPO = new ExportFrame("com/example/Repo", "query", 10);

    @Test
    void roundTripsFramesNamesAndSampleTotals() throws IOException {
        List<ExportSample> samples = List.of(
                new ExportSample(List.of(NATIVE_ROOT, SERVICE), new long[]{3, 5_000}),
                new ExportSample(List.of(NATIVE_ROOT, REPO), new long[]{7, 9_000}));

        byte[] gz = new PprofProfileWriter().write(List.of(SAMPLES, CPU), samples, TIME_NANOS, DURATION_NANOS);

        Profile profile = Profile.parseFrom(gunzip(gz));

        // pprof invariants
        assertEquals("", profile.getStringTable(0), "string_table[0] must be empty");
        assertEquals(2, profile.getSampleTypeCount());
        assertEquals(2, profile.getSampleCount());
        assertEquals(TIME_NANOS, profile.getTimeNanos());
        assertEquals(DURATION_NANOS, profile.getDurationNanos());
        // 3 distinct frames -> 3 functions, 3 locations
        assertEquals(3, profile.getFunctionCount());
        assertEquals(3, profile.getLocationCount());

        // read back with the real reader
        RecordingEventWriterStub stub = new RecordingEventWriterStub();
        new PprofProfileReader(stub).read(profile);

        // two stacks recovered, root-first, with the split class/method names intact
        assertEquals(2, stub.stacktracesById.size());
        EventStacktrace serviceStack = findLeaf(stub, "process");
        assertEquals(
                List.of(frame("", "Thread::call_run"), frame("com/example/Service", "process")),
                names(serviceStack));

        // samples dimension (count) sums to 10; the reader names it pprof.samples
        long totalSamples = stub.events.stream()
                .filter(e -> "samples".equals(e.eventType()))
                .mapToLong(Event::samples)
                .sum();
        assertEquals(10, totalSamples);

        // cpu dimension (nanoseconds) is carried as weight, not samples
        long totalCpuWeight = stub.events.stream()
                .filter(e -> "cpu".equals(e.eventType()))
                .mapToLong(e -> e.weight() == null ? 0 : e.weight())
                .sum();
        assertEquals(14_000, totalCpuWeight);
        assertFalse(stub.events.isEmpty());
    }

    @Test
    void exportsDottedNamesAndMarksDotMethodsWithHash() throws IOException {
        // A normal C++ frame (method has no dot) and a native libc symbol whose method carries a dot.
        ExportFrame cpp = new ExportFrame("libjvm.so", "CompileBroker::compiler_thread_loop", 0);
        ExportFrame nativeDotted = new ExportFrame("libc.so.6", "__sched_yield.cold", 0);
        List<ExportSample> samples = List.of(
                new ExportSample(List.of(cpp, nativeDotted), new long[]{1, 100}));

        byte[] gz = new PprofProfileWriter().write(List.of(SAMPLES, CPU), samples, TIME_NANOS, DURATION_NANOS);
        Profile profile = Profile.parseFrom(gunzip(gz));
        List<String> strings = profile.getStringTableList();

        // Idiomatic dotted form for the common case; '#' only where the method already has a dot.
        assertTrue(strings.contains("libjvm.so.CompileBroker::compiler_thread_loop"),
                "C++ frame should export dotted: " + strings);
        assertFalse(strings.contains("libjvm.so#CompileBroker::compiler_thread_loop"),
                "C++ frame must not use '#': " + strings);
        assertTrue(strings.contains("libc.so.6#__sched_yield.cold"),
                "dot-carrying method must be delimited with '#' to stay lossless: " + strings);

        // Both survive the round-trip back to split class/method columns.
        RecordingEventWriterStub stub = new RecordingEventWriterStub();
        new PprofProfileReader(stub).read(profile);
        EventStacktrace stack = findLeaf(stub, "__sched_yield.cold");
        assertEquals(
                List.of(frame("libjvm.so", "CompileBroker::compiler_thread_loop"),
                        frame("libc.so.6", "__sched_yield.cold")),
                names(stack));
    }

    private static EventStacktrace findLeaf(RecordingEventWriterStub stub, String leafMethod) {
        return stub.stacktracesById.values().stream()
                .filter(st -> {
                    List<EventFrame> frames = st.frames();
                    return !frames.isEmpty() && frames.get(frames.size() - 1).method().equals(leafMethod);
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("No stack with leaf method: " + leafMethod));
    }

    private static List<String> names(EventStacktrace stack) {
        return stack.frames().stream().map(f -> frame(f.clazz(), f.method())).toList();
    }

    private static String frame(String clazz, String method) {
        return clazz + "#" + method;
    }

    private static byte[] gunzip(byte[] gz) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            return in.readAllBytes();
        }
    }
}
