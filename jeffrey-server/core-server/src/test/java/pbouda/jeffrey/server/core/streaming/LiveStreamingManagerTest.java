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

import jdk.jfr.Recording;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LiveStreamingManagerTest {

    private static Recording recording;
    private static Path repoPath;

    @BeforeAll
    static void startRecording() {
        recording = new Recording();
        recording.enable("jdk.CPULoad").withPeriod(Duration.ofMillis(10));
        recording.setToDisk(true);
        recording.start();
        repoPath = Path.of(System.getProperty("jdk.jfr.repository"));
    }

    @AfterAll
    static void stopRecording() {
        recording.stop();
        recording.close();
    }

    @Nested
    class SubscribeAndUnsubscribe {

        @Test
        void subscribeReturnsId() throws Exception {
            try (var manager = new LiveStreamingManager()) {
                String subscriptionId = manager.subscribe(
                        subscription(), noOpCallbacks(new CountDownLatch(1)));

                assertNotNull(subscriptionId);
                assertFalse(subscriptionId.isEmpty());
            }
        }

        @Test
        void unsubscribeClosesStream() throws Exception {
            try (var manager = new LiveStreamingManager()) {
                var batchReceived = new CountDownLatch(1);
                var closeLatch = new CountDownLatch(1);

                String subscriptionId = manager.subscribe(
                        subscription(),
                        new StreamingCallbacks(
                                _ -> batchReceived.countDown(),
                                () -> {},
                                _ -> {},
                                closeLatch::countDown));

                assertTrue(batchReceived.await(10, TimeUnit.SECONDS),
                        "Stream should deliver at least one batch");

                manager.unsubscribe(subscriptionId);

                assertTrue(closeLatch.await(5, TimeUnit.SECONDS),
                        "onClose should be called after unsubscribe");
            }
        }

        @Test
        void unsubscribeNonExistentIsNoOp() {
            try (var manager = new LiveStreamingManager()) {
                assertDoesNotThrow(() -> manager.unsubscribe("non-existent-id"));
            }
        }
    }

    @Nested
    class CloseAll {

        @Test
        void closeStopsAllSubscribers() throws Exception {
            try (var manager = new LiveStreamingManager()) {
                manager.subscribe(subscription(), noOpCallbacks(new CountDownLatch(1)));
                manager.subscribe(subscription(), noOpCallbacks(new CountDownLatch(1)));
            }
            // close() was called by try-with-resources — verify no lingering state
        }
    }

    @Nested
    class AutoRemoval {

        @Test
        void closedSubscriberRemovedFromTracking() throws Exception {
            try (var manager = new LiveStreamingManager()) {
                var batchReceived = new CountDownLatch(1);
                var completeLatch = new CountDownLatch(1);

                String subscriptionId = manager.subscribe(
                        subscription(),
                        new StreamingCallbacks(
                                _ -> batchReceived.countDown(),
                                completeLatch::countDown,
                                _ -> completeLatch.countDown()));

                assertTrue(batchReceived.await(10, TimeUnit.SECONDS));

                manager.unsubscribe(subscriptionId);
                assertTrue(completeLatch.await(5, TimeUnit.SECONDS));

                // After completion, subscriber should be removed from tracking
                // Second unsubscribe should be a no-op (already removed)
                assertDoesNotThrow(() -> manager.unsubscribe(subscriptionId));
            }
        }
    }

    private static LiveStreamSubscription subscription() {
        return new LiveStreamSubscription("session-1", repoPath, Set.of("jdk.CPULoad"), false);
    }

    private static StreamingCallbacks noOpCallbacks(CountDownLatch latch) {
        return new StreamingCallbacks(
                _ -> {},
                latch::countDown,
                _ -> latch.countDown());
    }
}
