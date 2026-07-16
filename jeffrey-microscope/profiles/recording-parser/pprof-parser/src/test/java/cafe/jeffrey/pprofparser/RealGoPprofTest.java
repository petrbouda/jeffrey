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
import cafe.jeffrey.provider.profile.api.EventFrame;
import cafe.jeffrey.provider.profile.api.EventStacktrace;
import cafe.jeffrey.provider.profile.api.EventType;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parses real pprof profiles produced by Go's {@code runtime/pprof} (gzip-compressed protobuf),
 * exercising the parser against actual producer output rather than synthetic fixtures.
 */
class RealGoPprofTest {

    @Test
    void parsesGoCpuProfile() {
        RecordingEventWriterStub writer = read("pprof/go-cpu.pprof");

        List<String> typeNames = writer.eventTypes.stream().map(EventType::name).toList();
        assertTrue(typeNames.contains("pprof.samples"), typeNames.toString());
        assertTrue(typeNames.contains("pprof.cpu"), typeNames.toString());
        assertFalse(writer.events.isEmpty());

        // The Go generator recurses through fib(), so at least one Go frame must be present and
        // the stacks are mapped root-first (runtime.main sits at the root).
        assertTrue(containsFrame(writer, "main", "fib"));
        assertEquals("runtime", rootFrame(writer).clazz());
    }

    @Test
    void parsesGoHeapProfileWithFourDimensions() {
        RecordingEventWriterStub writer = read("pprof/go-heap.pprof");

        List<String> typeNames = writer.eventTypes.stream().map(EventType::name).toList();
        assertTrue(typeNames.contains("pprof.alloc_objects"), typeNames.toString());
        assertTrue(typeNames.contains("pprof.alloc_space"), typeNames.toString());
        assertTrue(typeNames.contains("pprof.inuse_objects"), typeNames.toString());
        assertTrue(typeNames.contains("pprof.inuse_space"), typeNames.toString());
        assertFalse(writer.events.isEmpty());
    }

    private static RecordingEventWriterStub read(String resource) {
        Profile profile = new PprofStreamReader().read(resourcePath(resource));
        RecordingEventWriterStub writer = new RecordingEventWriterStub();
        new PprofProfileReader(writer).read(profile);
        return writer;
    }

    private static boolean containsFrame(RecordingEventWriterStub writer, String clazz, String method) {
        return writer.stacktracesById.values().stream()
                .flatMap(stacktrace -> stacktrace.frames().stream())
                .anyMatch(frame -> frame.clazz().equals(clazz) && frame.method().equals(method));
    }

    private static EventFrame rootFrame(RecordingEventWriterStub writer) {
        EventStacktrace deepest = writer.stacktracesById.values().stream()
                .max((a, b) -> Integer.compare(a.frames().size(), b.frames().size()))
                .orElseThrow();
        return deepest.frames().get(0);
    }

    private static Path resourcePath(String resource) {
        try {
            return Path.of(RealGoPprofTest.class.getClassLoader().getResource(resource).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Missing test resource: " + resource, e);
        }
    }
}
