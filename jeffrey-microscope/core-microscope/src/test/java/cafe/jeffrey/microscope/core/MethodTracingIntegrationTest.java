/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core;

import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JDK 25's method tracing (JEP 520), end to end, over a real recording: {@code MethodTraceFixture},
 * whose {@code outer()} calls {@code inner()} five times over, traced with {@code jdk.MethodTrace}.
 * Every figure is what the JDK's {@code RecordedEvent.getDuration()} reads from the file — its end less
 * its start, each converted from ticks on its own, which for three of the {@code outer()} calls is a
 * nanosecond more than the event's converted duration field {@code jfr print} shows.
 */
class MethodTracingIntegrationTest extends CheckoutVolumeMicroscope {

    /** The recording, kept with the parser's tests. */
    private static final Path RECORDING = Path.of("..", "profiles", "profile-management", "src", "test", "resources",
            "jfr", "method-trace.jfr");

    private static final String FIXTURE = "MethodTraceFixture";
    private static final String INNER = "inner";
    private static final String OUTER = "outer";
    private static final String METHOD_TRACE = "jdk.MethodTrace";
    private static final String MAIN_THREAD = "main";

    /** Each method's calls, the nanoseconds they took together and the longest of them, as the file holds them. */
    private static final Map<String, List<Long>> TRACED = Map.of(
            INNER, List.of(5L, 25_791_416L, 5_207_004L),
            OUTER, List.of(5L, 26_230_943L, 5_505_963L));

    private static final Path RUN = Path.of("target", "method-trace-it", UUID.randomUUID().toString()).toAbsolutePath();

    private static String methodTraceProfile;

    /** Every call, its time and the slowest of each method. */
    @Test
    void theOverviewHoldsEveryTracedCall() {
        JsonNode overview = microscope.answer("methodtracing_overview", pid());
        Map<String, List<Long>> byMethod = new HashMap<>();
        overview.path("topMethodsByCount").forEach(row -> byMethod.put(row.path("methodName").asString(), List.of(
                row.path("invocationCount").asLong(), row.path("totalDurationNanos").asLong(),
                row.path("maxDurationNanos").asLong())));
        JsonNode header = overview.path("header");

        assertEquals("OK", overview.path("status").asString(), overview::toString);
        assertEquals(TRACED, byMethod);
        overview.path("topMethodsByCount").forEach(row -> assertEquals(FIXTURE, row.path("className").asString()));
        assertEquals(10, header.path("totalInvocations").asLong());
        assertEquals(2, header.path("uniqueMethodCount").asLong());
        assertEquals(TRACED.get(INNER).get(1) + TRACED.get(OUTER).get(1), header.path("totalDurationNanos").asLong());
        assertEquals(TRACED.get(OUTER).get(2), header.path("maxDurationNanos").asLong());
    }

    /** The slowest calls, slowest first, the first the longest {@code outer()} on {@code main}. */
    @Test
    void theSlowestCallsComeSlowestFirst() {
        JsonNode invocations = microscope.answer("methodtracing_slowest", pid()).path("invocations");
        List<Long> durations = new ArrayList<>();
        invocations.forEach(invocation -> durations.add(invocation.path("durationNanos").asLong()));

        assertEquals(10, durations.size());
        assertEquals(durations.stream().sorted((a, b) -> Long.compare(b, a)).toList(), durations);
        assertEquals(TRACED.get(OUTER).get(2), durations.getFirst());
        assertEquals(OUTER, invocations.path(0).path("methodName").asString());
        assertEquals(MAIN_THREAD, invocations.path(0).path("threadName").asString());
    }

    /**
     * A traced call's own time is its duration less the traced calls inside it: {@code inner()} keeps
     * all of its own, {@code outer()} only what {@code inner()} did not take.
     */
    @Test
    void aCallsOwnTimeLeavesOutTheTracedCallsInsideIt() {
        List<List<String>> selfByMethod = query(methodTraceProfile(), "SELECT json_extract_string(fields, '$.method')"
                + " LIKE '%" + INNER + "%', sum(duration), sum(weight) FROM events WHERE event_type = '" + METHOD_TRACE
                + "' GROUP BY 1 ORDER BY 1");

        assertEquals(List.of(
                        List.of("false", String.valueOf(TRACED.get(OUTER).get(1)),
                                String.valueOf(TRACED.get(OUTER).get(1) - TRACED.get(INNER).get(1))),
                        List.of("true", String.valueOf(TRACED.get(INNER).get(1)), String.valueOf(TRACED.get(INNER).get(1)))),
                selfByMethod);
    }

    private ObjectNode pid() {
        return Json.createObject().put("profileId", methodTraceProfile());
    }

    private String methodTraceProfile() {
        synchronized (MethodTracingIntegrationTest.class) {
            if (methodTraceProfile == null) {
                JsonNode imported = microscope.answer("recordings_analyzeFile",
                        Json.createObject().put("path", recordingFile().toString()));
                assertEquals("READY", imported.path("status").asString(), imported::toString);
                methodTraceProfile = imported.path("profileId").asString();
            }
            return methodTraceProfile;
        }
    }

    private static Path recordingFile() {
        try (InputStream in = Files.newInputStream(RECORDING)) {
            Files.createDirectories(RUN);
            Path file = RUN.resolve(RECORDING.getFileName());
            Files.write(file, in.readAllBytes());
            assertTrue(Files.size(file) > 0);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
