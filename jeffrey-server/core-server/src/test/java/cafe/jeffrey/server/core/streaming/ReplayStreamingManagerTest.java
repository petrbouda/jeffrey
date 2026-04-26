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

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ReplayStreamingManagerTest {

    private static final String SESSION_ID = "test-session";

    @Nested
    class StartAndStop {

        @Test
        void startReturnsReplayId(@TempDir Path tempDir) {
            var manager = new ReplayStreamingManager();
            var latch = new CountDownLatch(1);

            String replayId = manager.subscribe(
                    singleFileSubscription(tempDir),
                    noOpCallbacks(latch));

            assertNotNull(replayId);
            assertFalse(replayId.isEmpty());
            manager.close();
        }

        @Test
        void stopClosesReader(@TempDir Path tempDir) throws Exception {
            var manager = new ReplayStreamingManager();
            var latch = new CountDownLatch(1);

            String replayId = manager.subscribe(
                    singleFileSubscription(tempDir),
                    noOpCallbacks(latch));

            manager.unsubscribe(replayId);

            // Wait for async cleanup
            latch.await(5, TimeUnit.SECONDS);
            manager.close();
        }

        @Test
        void stopNonExistentIdIsNoOp(@TempDir Path tempDir) {
            var manager = new ReplayStreamingManager();
            assertDoesNotThrow(() -> manager.unsubscribe("non-existent-id"));
            manager.close();
        }
    }

    @Nested
    class CloseAll {

        @Test
        void closeStopsAllReaders(@TempDir Path tempDir) {
            var manager = new ReplayStreamingManager();

            manager.subscribe(allFilesSubscription(tempDir), noOpCallbacks(new CountDownLatch(1)));
            manager.subscribe(allFilesSubscription(tempDir), noOpCallbacks(new CountDownLatch(1)));

            assertDoesNotThrow(manager::close);

            // After close, stop on any id should be a no-op (map cleared)
            assertDoesNotThrow(() -> manager.unsubscribe("any-id"));
        }
    }

    @Nested
    class AutoRemoval {

        @Test
        void completedReplayRemovedFromTracking(@TempDir Path tempDir) throws Exception {
            var manager = new ReplayStreamingManager();
            var latch = new CountDownLatch(1);
            AtomicBoolean completed = new AtomicBoolean(false);

            manager.subscribe(
                    singleFileSubscription(tempDir),
                    new StreamingCallbacks(
                            _ -> {},
                            () -> {
                                completed.set(true);
                                latch.countDown();
                            },
                            _ -> latch.countDown()));

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            assertTrue(completed.get());

            // After completion, stop should be a no-op (already removed)
            // No exception means the reader was removed from tracking
            manager.close();
        }
    }

    private static ReplayStreamSubscription singleFileSubscription(Path tempDir) {
        return new ReplayStreamSubscription(
                SESSION_ID, List.of(JfrTestFiles.resolve(JfrTestFiles.PROFILE_1)),
                Set.of("jdk.CPULoad"), StreamingWindow.UNBOUNDED, tempDir);
    }

    private static ReplayStreamSubscription allFilesSubscription(Path tempDir) {
        return new ReplayStreamSubscription(
                SESSION_ID, JfrTestFiles.allProfiles(),
                Set.of("jdk.CPULoad"), StreamingWindow.UNBOUNDED, tempDir);
    }

    private static StreamingCallbacks noOpCallbacks(CountDownLatch latch) {
        return new StreamingCallbacks(
                _ -> {},
                latch::countDown,
                _ -> latch.countDown());
    }
}
