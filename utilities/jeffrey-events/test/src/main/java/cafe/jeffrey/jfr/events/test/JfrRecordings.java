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

package cafe.jeffrey.jfr.events.test;

import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Runs a body inside a real flight recording and hands back what it committed.
 * <p>
 * Real recordings on purpose: commit paths, {@code @Inherited} metadata, threshold behaviour and
 * the ids a span carries are exactly what instrumentation tests need to pin, and none of it is
 * observable without going through JFR itself. Asserting on a mock would prove only that the mock
 * was called.
 * <p>
 * The threshold is pinned to zero for every enabled type, so events that take no measurable time —
 * which is most of them in a test — are still recorded whatever the enclosing JFR settings say.
 *
 * <pre>{@code
 * List<RecordedEvent> events = JfrRecordings.all("jeffrey.HttpServerExchange", () -> {
 *     mockMvc.perform(get("/api/users/1"));
 * });
 * SpansAssert.assertThat(events).hasSpan("GET /api/users/{id}").isRoot();
 * }</pre>
 */
public final class JfrRecordings {

    private static final String DUMP_PREFIX = "jeffrey-events-test";
    private static final String DUMP_SUFFIX = ".jfr";

    private JfrRecordings() {
    }

    /**
     * Every event of the given type the body committed, in commit order.
     *
     * @param eventType the JFR event name, e.g. {@code "jeffrey.TraceSpan"}
     */
    public static List<RecordedEvent> all(String eventType, Runnable body) throws IOException {
        Objects.requireNonNull(eventType, "eventType must not be null");
        return all(Set.of(eventType), body);
    }

    /**
     * Every event of any of the given types the body committed, in commit order — the form for an
     * assertion that spans several event types at once, e.g. a request event and the statements
     * that ran inside it.
     */
    public static List<RecordedEvent> all(Collection<String> eventTypes, Runnable body) throws IOException {
        Objects.requireNonNull(eventTypes, "eventTypes must not be null");
        Objects.requireNonNull(body, "body must not be null");
        if (eventTypes.isEmpty()) {
            throw new IllegalArgumentException("at least one event type must be enabled");
        }

        Set<String> enabled = Set.copyOf(eventTypes);
        Path dump = Files.createTempFile(DUMP_PREFIX, DUMP_SUFFIX);
        try (Recording recording = new Recording()) {
            for (String eventType : enabled) {
                recording.enable(eventType).withThreshold(Duration.ZERO);
            }
            recording.start();
            body.run();
            recording.stop();
            recording.dump(dump);

            return RecordingFile.readAllEvents(dump).stream()
                    .filter(event -> enabled.contains(event.getEventType().getName()))
                    .toList();
        } finally {
            Files.deleteIfExists(dump);
        }
    }

    /**
     * The form of {@link #all(String, Runnable)} that names the event by its class rather than its
     * JFR name, for a caller that has the class to hand.
     */
    public static List<RecordedEvent> all(Class<? extends Event> eventClass, Runnable body) throws IOException {
        Objects.requireNonNull(eventClass, "eventClass must not be null");
        Objects.requireNonNull(body, "body must not be null");

        Path dump = Files.createTempFile(DUMP_PREFIX, DUMP_SUFFIX);
        try (Recording recording = new Recording()) {
            recording.enable(eventClass).withThreshold(Duration.ZERO);
            recording.start();
            body.run();
            recording.stop();
            recording.dump(dump);

            return List.copyOf(RecordingFile.readAllEvents(dump));
        } finally {
            Files.deleteIfExists(dump);
        }
    }

    /**
     * The one event of the given type the body committed.
     *
     * @throws AssertionError when the body committed anything other than exactly one
     */
    public static RecordedEvent single(String eventType, Runnable body) throws IOException {
        List<RecordedEvent> events = all(eventType, body);
        if (events.size() != 1) {
            throw new AssertionError(
                    "expected exactly one " + eventType + " event, but recorded " + events.size());
        }
        return events.getFirst();
    }
}
