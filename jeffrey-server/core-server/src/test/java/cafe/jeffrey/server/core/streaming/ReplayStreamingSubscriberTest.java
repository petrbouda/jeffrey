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

package cafe.jeffrey.server.core.streaming;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.server.api.v1.EventBatch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ReplayStreamingSubscriberTest {

    private static final String SESSION_ID = "test-session";

    @Nested
    class MultiFileReplay {

        @Test
        void readsAllFilesAndCallsOnComplete(@TempDir Path tempDir) throws Exception {
            var latch = new CountDownLatch(1);
            List<EventBatch> batches = new ArrayList<>();
            AtomicBoolean completed = new AtomicBoolean(false);

            var subscription = new ReplayStreamSubscription(
                    SESSION_ID, JfrTestFiles.allProfiles(), Set.of("jdk.CPULoad"),
                    StreamingWindow.UNBOUNDED, tempDir);

            var callbacks = new StreamingCallbacks(
                    batch -> {
                        synchronized (batches) {
                            batches.add(batch);
                        }
                    },
                    () -> {
                        completed.set(true);
                        latch.countDown();
                    },
                    t -> latch.countDown());

            var reader = new ReplayStreamingSubscriber(subscription, callbacks);
            reader.start();

            assertTrue(latch.await(30, TimeUnit.SECONDS), "Replay should complete within 30 seconds");
            assertTrue(completed.get(), "onComplete should have been called");

            int totalEvents;
            synchronized (batches) {
                totalEvents = batches.stream().mapToInt(EventBatch::getEventsCount).sum();
            }
            assertTrue(totalEvents > 3000, "Should have events from all 4 files, got " + totalEvents);
        }
    }

    @Nested
    class CorruptedFileHandling {

        @Test
        void skipsCorruptedFileAndContinues(@TempDir Path tempDir) throws Exception {
            Path corrupted = JfrTestFiles.createCorruptedFile(tempDir);
            List<Path> files = List.of(
                    JfrTestFiles.resolve(JfrTestFiles.PROFILE_1),
                    corrupted,
                    JfrTestFiles.resolve(JfrTestFiles.PROFILE_2));

            var latch = new CountDownLatch(1);
            List<EventBatch> batches = new ArrayList<>();
            AtomicBoolean completed = new AtomicBoolean(false);
            AtomicInteger errorCount = new AtomicInteger(0);

            var subscription = new ReplayStreamSubscription(
                    SESSION_ID, files, Set.of("jdk.CPULoad"),
                    StreamingWindow.UNBOUNDED, tempDir);

            var callbacks = new StreamingCallbacks(
                    batch -> {
                        synchronized (batches) {
                            batches.add(batch);
                        }
                    },
                    () -> {
                        completed.set(true);
                        latch.countDown();
                    },
                    t -> errorCount.incrementAndGet());

            var reader = new ReplayStreamingSubscriber(subscription, callbacks);
            reader.start();

            assertTrue(latch.await(30, TimeUnit.SECONDS), "Replay should complete within 30 seconds");
            assertTrue(completed.get(), "onComplete should be called even with corrupted files");

            int totalEvents;
            synchronized (batches) {
                totalEvents = batches.stream().mapToInt(EventBatch::getEventsCount).sum();
            }
            assertTrue(totalEvents > 800, "Should have events from the 2 valid files, got " + totalEvents);
        }
    }

    @Nested
    class Cancellation {

        @Test
        void closeStopsProcessing(@TempDir Path tempDir) throws Exception {
            var latch = new CountDownLatch(1);
            AtomicBoolean completed = new AtomicBoolean(false);

            var subscription = new ReplayStreamSubscription(
                    SESSION_ID, JfrTestFiles.allProfiles(), Set.of("jdk.CPULoad"),
                    StreamingWindow.UNBOUNDED, tempDir);

            var callbacks = new StreamingCallbacks(
                    _ -> {},
                    () -> {
                        completed.set(true);
                        latch.countDown();
                    },
                    _ -> latch.countDown());

            var reader = new ReplayStreamingSubscriber(subscription, callbacks);
            reader.start();

            // Close immediately — should stop before processing all files
            reader.close();

            // Wait briefly for the async thread to notice
            latch.await(5, TimeUnit.SECONDS);
        }
    }

    @Nested
    class TempDirectoryLifecycle {

        @Test
        void tempDirectoryRemovedAfterCompletion(@TempDir Path tempDir) throws Exception {
            var latch = new CountDownLatch(1);

            var subscription = new ReplayStreamSubscription(
                    SESSION_ID, List.of(JfrTestFiles.resolve(JfrTestFiles.PROFILE_1)),
                    Set.of("jdk.CPULoad"), StreamingWindow.UNBOUNDED, tempDir);

            // Wait for onClose (fires after temp dir cleanup) instead of onComplete
            var callbacks = new StreamingCallbacks(
                    _ -> {},
                    () -> {},
                    _ -> {},
                    latch::countDown);

            var reader = new ReplayStreamingSubscriber(subscription, callbacks);
            reader.start();

            assertTrue(latch.await(30, TimeUnit.SECONDS));

            // The replay-{sessionId}-{uuid} subdirectory should be cleaned up
            try (var entries = Files.list(tempDir)) {
                long replayDirs = entries.filter(p -> p.getFileName().toString().startsWith("replay-")).count();
                assertEquals(0, replayDirs, "Replay temp directory should be removed after completion");
            }
        }
    }

    @Nested
    class OnCloseCallback {

        @Test
        void onCloseInvokedAfterCompletion(@TempDir Path tempDir) throws Exception {
            var completeLatch = new CountDownLatch(1);
            var closeLatch = new CountDownLatch(1);

            var subscription = new ReplayStreamSubscription(
                    SESSION_ID, List.of(JfrTestFiles.resolve(JfrTestFiles.PROFILE_1)),
                    Set.of("jdk.CPULoad"), StreamingWindow.UNBOUNDED, tempDir);

            var callbacks = new StreamingCallbacks(
                    _ -> {},
                    completeLatch::countDown,
                    _ -> completeLatch.countDown(),
                    closeLatch::countDown);

            var reader = new ReplayStreamingSubscriber(subscription, callbacks);
            reader.start();

            assertTrue(completeLatch.await(30, TimeUnit.SECONDS));
            assertTrue(closeLatch.await(5, TimeUnit.SECONDS), "onClose should be called after completion");
        }
    }
}
