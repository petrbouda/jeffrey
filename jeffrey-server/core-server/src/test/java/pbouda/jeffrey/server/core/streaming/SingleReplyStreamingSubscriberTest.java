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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pbouda.jeffrey.server.api.v1.EventBatch;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SingleReplyStreamingSubscriberTest {

    private static final String SESSION_ID = "test-session";

    // profile-1.jfr: start 2025-12-20T00:12:24Z, duration 900s
    private static final Instant RECORDING_START = Instant.parse("2025-12-20T00:12:24Z");
    private static final Instant RECORDING_END = RECORDING_START.plusSeconds(900);

    @Nested
    class ReadEvents {

        @Test
        void readsAllEventsOfType(@TempDir Path tempDir) throws IOException {
            var batches = readFile(JfrTestFiles.PROFILE_1, Set.of("jdk.CPULoad"),
                    StreamingWindow.UNBOUNDED, tempDir);

            int totalEvents = batches.stream().mapToInt(EventBatch::getEventsCount).sum();
            assertTrue(totalEvents > 800, "Expected ~899 CPULoad events, got " + totalEvents);

            batches.stream()
                    .flatMap(b -> b.getEventsList().stream())
                    .forEach(e -> assertEquals("jdk.CPULoad", e.getEventType()));
        }

        @Test
        void filtersByEventType(@TempDir Path tempDir) throws IOException {
            var batches = readFile(JfrTestFiles.PROFILE_1, Set.of("jdk.GCHeapSummary"),
                    StreamingWindow.UNBOUNDED, tempDir);

            int totalEvents = batches.stream().mapToInt(EventBatch::getEventsCount).sum();
            assertEquals(1, totalEvents, "profile-1.jfr has exactly 1 GCHeapSummary event");
        }

        @Test
        void emptyResultForNonExistentType(@TempDir Path tempDir) throws IOException {
            var batches = readFile(JfrTestFiles.PROFILE_1, Set.of("jdk.NonExistent"),
                    StreamingWindow.UNBOUNDED, tempDir);

            int totalEvents = batches.stream().mapToInt(EventBatch::getEventsCount).sum();
            assertEquals(0, totalEvents);
        }
    }

    @Nested
    class TimeWindowFiltering {

        @Test
        void windowRestrictsEvents(@TempDir Path tempDir) throws IOException {
            // First 5 minutes of the 15-minute recording
            var window = new StreamingWindow(RECORDING_START, RECORDING_START.plusSeconds(300));

            var batches = readFile(JfrTestFiles.PROFILE_1, Set.of("jdk.CPULoad"), window, tempDir);

            int totalEvents = batches.stream().mapToInt(EventBatch::getEventsCount).sum();
            // ~1 CPULoad per second, 300 seconds → roughly 300 events (not 899)
            assertTrue(totalEvents > 200 && totalEvents < 400,
                    "Expected ~300 events in 5-minute window, got " + totalEvents);
        }

        @Test
        void windowAfterRecordingReturnsNoEvents(@TempDir Path tempDir) throws IOException {
            var window = new StreamingWindow(RECORDING_END.plusSeconds(3600), null);

            var batches = readFile(JfrTestFiles.PROFILE_1, Set.of("jdk.CPULoad"), window, tempDir);

            int totalEvents = batches.stream().mapToInt(EventBatch::getEventsCount).sum();
            assertEquals(0, totalEvents);
        }
    }

    @Nested
    class BatchSize {

        @Test
        void eachBatchHasAtMostBatchSizeEvents(@TempDir Path tempDir) throws IOException {
            var batches = readFile(JfrTestFiles.PROFILE_1, Set.of("jdk.CPULoad"),
                    StreamingWindow.UNBOUNDED, tempDir);

            assertFalse(batches.isEmpty(), "Should produce at least one batch");
            for (EventBatch batch : batches) {
                assertTrue(batch.getEventsCount() <= 1000,
                        "Batch has " + batch.getEventsCount() + " events, expected <= 1000");
            }
        }
    }

    @Nested
    class Cancellation {

        @Test
        void respectsClosedFlag(@TempDir Path tempDir) throws IOException {
            var subscription = new ReplayStreamSubscription(
                    SESSION_ID, List.of(), Set.of("jdk.CPULoad"), StreamingWindow.UNBOUNDED, tempDir);

            AtomicBoolean closed = new AtomicBoolean(true);
            List<EventBatch> batches = new ArrayList<>();
            AtomicInteger errorCount = new AtomicInteger(0);

            var reader = new SingleReplyStreamingSubscriber(
                    subscription, tempDir, batches::add, _ -> errorCount.incrementAndGet(), closed::get);

            reader.read(JfrTestFiles.resolve(JfrTestFiles.PROFILE_1));

            int totalEvents = batches.stream().mapToInt(EventBatch::getEventsCount).sum();
            assertEquals(0, totalEvents, "Should produce no events when closed immediately");
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void reportsErrorsForCorruptedFile(@TempDir Path tempDir) throws IOException {
            Path corrupted = JfrTestFiles.createCorruptedFile(tempDir);
            var subscription = new ReplayStreamSubscription(
                    SESSION_ID, List.of(), Set.of("jdk.CPULoad"), StreamingWindow.UNBOUNDED, tempDir);

            List<EventBatch> batches = new ArrayList<>();
            AtomicInteger errorCount = new AtomicInteger(0);

            var reader = new SingleReplyStreamingSubscriber(
                    subscription, tempDir, batches::add, _ -> errorCount.incrementAndGet(), () -> false);

            // Should not throw — errors reported via callback
            assertDoesNotThrow(() -> reader.read(corrupted));
        }
    }

    private List<EventBatch> readFile(
            String jfrFileName, Set<String> eventTypes, StreamingWindow window, Path tempDir) throws IOException {

        var subscription = new ReplayStreamSubscription(
                SESSION_ID, List.of(), eventTypes, window, tempDir);

        List<EventBatch> batches = new ArrayList<>();
        AtomicInteger errorCount = new AtomicInteger(0);

        var reader = new SingleReplyStreamingSubscriber(
                subscription, tempDir, batches::add, _ -> errorCount.incrementAndGet(), () -> false);

        reader.read(JfrTestFiles.resolve(jfrFileName));
        return batches;
    }
}
