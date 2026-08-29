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

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.shared.common.model.Type;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@code weight_entity} means for {@code jdk.MethodTrace}, pinned against a real recording.
 * <p>
 * This is deliberately not a test with mocked events. The bug it guards was not a coding slip but a
 * wrong belief about the event — that its stack trace is rooted at the traced method, as every other
 * stack-carrying event's is. JEP 520 roots it at the <em>caller</em>. A mock would have encoded the
 * same wrong belief and passed, so the fixture is a genuine JDK 25 recording of a two-method call
 * chain, both methods traced:
 * <pre>
 *   method = MethodTraceFixture.inner()   stackTrace = [ MethodTraceFixture.outer(), main ]
 *   method = MethodTraceFixture.outer()   stackTrace = [ MethodTraceFixture.main(), ... ]
 * </pre>
 */
@DisplayName("jdk.MethodTrace weight entity")
class MethodTraceWeightEntityTest {

    private static final String FIXTURE = "/jfr/method-trace.jfr";
    private static final String INNER = "MethodTraceFixture#inner";
    private static final String OUTER = "MethodTraceFixture#outer";
    private static final String MAIN = "MethodTraceFixture#main";

    private static List<RecordedEvent> methodTraces;

    @BeforeAll
    static void readFixture() throws IOException, URISyntaxException {
        Path path = Path.of(MethodTraceWeightEntityTest.class.getResource(FIXTURE).toURI());
        try (RecordingFile file = new RecordingFile(path)) {
            List<RecordedEvent> events = new ArrayList<>();
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                if (EventTypeName.METHOD_TRACE.equals(event.getEventType().getName())) {
                    events.add(event);
                }
            }
            methodTraces = List.copyOf(events);
        }
    }

    private static String entityOf(RecordedEvent event) {
        return WeightExtractorRegistry.resolve(Type.METHOD_TRACE).entityExtractor().apply(event);
    }

    private static String leafFrameOf(RecordedEvent event) {
        List<RecordedFrame> frames = event.getStackTrace().getFrames();
        RecordedMethod method = frames.getFirst().getMethod();
        return method.getType().getName() + "#" + method.getName();
    }

    private static String tracedMethodOf(RecordedEvent event) {
        RecordedMethod method = event.getValue("method");
        return method.getType().getName() + "#" + method.getName();
    }

    @Test
    @DisplayName("the fixture really does root its stack trace at the caller")
    void fixtureShowsTheDiscrepancy() {
        // Guards the premise rather than the code: if a future JDK ever roots the stack at the traced
        // method, this fails first and explains why the rest of the file exists.
        assertFalse(methodTraces.isEmpty(), "fixture has no jdk.MethodTrace events");
        assertTrue(
                methodTraces.stream().anyMatch(e -> !tracedMethodOf(e).equals(leafFrameOf(e))),
                "expected at least one event whose leaf frame is not the method it traced");
    }

    @Test
    @DisplayName("names the traced method, not the caller that appears at the stack leaf")
    void namesTheTracedMethod() {
        for (RecordedEvent event : methodTraces) {
            assertEquals(tracedMethodOf(event), entityOf(event),
                    "weight entity must be the event's own method field");
        }
    }

    @Test
    @DisplayName("never attributes an invocation to the method that called it")
    void neverNamesTheCaller() {
        Set<String> entities = methodTraces.stream()
                .map(MethodTraceWeightEntityTest::entityOf)
                .collect(Collectors.toUnmodifiableSet());

        // Both traced methods must be present under their own names ...
        assertTrue(entities.contains(INNER), "inner was traced and must appear as itself");
        assertTrue(entities.contains(OUTER), "outer was traced and must appear as itself");

        // ... and main must not, which is the sharpest statement of the old bug: it was never
        // selected by the filter and was never traced, yet it is the stack leaf of every outer
        // event, so the dashboard listed it as a traced method.
        assertFalse(entities.contains(MAIN), "main was never traced and must not be named");
    }

    @Test
    @DisplayName("splits back into the class and method the dashboard renders")
    void roundTripsThroughTheDashboardShape() {
        // The dashboard re-splits weight_entity on '#' into a JfrMethod. A name that does not carry
        // the separator would render with an empty method column.
        for (RecordedEvent event : methodTraces) {
            String entity = entityOf(event);
            assertNotNull(entity);
            assertEquals(2, entity.split("#").length, "expected 'Class#method': " + entity);
        }
    }
}
