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

import jdk.jfr.Recording;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.server.api.v1.EventBatch;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LiveStreamingSubscriberTest {

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
    class EventDelivery {

        @Test
        void receivesEventsViaOnNextCallback() throws Exception {
            var batches = Collections.synchronizedList(new ArrayList<EventBatch>());
            var latch = new CountDownLatch(3);

            var subscription = new LiveStreamSubscription(
                    "session-1", repoPath, Set.of("jdk.CPULoad"), false);
            var subscriber = new LiveStreamingSubscriber(subscription, new StreamingCallbacks(
                    batch -> {
                        batches.add(batch);
                        latch.countDown();
                    },
                    () -> {},
                    _ -> {}));

            subscriber.start();
            try {
                assertTrue(latch.await(10, TimeUnit.SECONDS), "Should receive at least 3 batches");
                assertFalse(batches.isEmpty());
                assertTrue(batches.stream().anyMatch(b -> b.getEventsCount() > 0),
                        "At least one batch should contain events");
            } finally {
                subscriber.close();
            }
        }

        @Test
        void filtersEventsByType() throws Exception {
            var batches = Collections.synchronizedList(new ArrayList<EventBatch>());
            var closeLatch = new CountDownLatch(1);

            var subscription = new LiveStreamSubscription(
                    "session-1", repoPath, Set.of("jdk.NonExistent"), false);
            var subscriber = new LiveStreamingSubscriber(subscription,
                    new StreamingCallbacks(_ -> batches.add(null), () -> {}, _ -> {}, closeLatch::countDown));

            subscriber.start();
            // Wait briefly then close — no events should have arrived for a non-existent type
            Thread.sleep(2000);
            subscriber.close();

            assertTrue(closeLatch.await(5, TimeUnit.SECONDS));
            assertTrue(batches.isEmpty(), "No batches should be received for non-existent event type");
        }
    }

    @Nested
    class SendEmptyBatches {

        @Test
        void sendsEmptyBatchWhenEnabled() throws Exception {
            var emptyBatchReceived = new CountDownLatch(1);

            var subscription = new LiveStreamSubscription(
                    "session-1", repoPath, Set.of("jdk.NonExistent"), true);
            var subscriber = new LiveStreamingSubscriber(subscription, new StreamingCallbacks(
                    batch -> {
                        if (batch.getEventsCount() == 0) {
                            emptyBatchReceived.countDown();
                        }
                    },
                    () -> {},
                    _ -> {}));

            subscriber.start();
            try {
                assertTrue(emptyBatchReceived.await(10, TimeUnit.SECONDS),
                        "Should receive at least one empty batch when sendEmptyBatches=true");
            } finally {
                subscriber.close();
            }
        }

        @Test
        void doesNotSendEmptyBatchWhenDisabled() throws Exception {
            var batches = Collections.synchronizedList(new ArrayList<EventBatch>());

            var subscription = new LiveStreamSubscription(
                    "session-1", repoPath, Set.of("jdk.NonExistent"), false);
            var subscriber = new LiveStreamingSubscriber(subscription, new StreamingCallbacks(
                    batches::add,
                    () -> {},
                    _ -> {}));

            subscriber.start();
            Thread.sleep(2000);
            subscriber.close();

            assertTrue(batches.isEmpty(),
                    "No batches should be received when sendEmptyBatches=false and no matching events");
        }
    }

    @Nested
    class Close {

        @Test
        void closeIsIdempotent() throws Exception {
            var subscription = new LiveStreamSubscription(
                    "session-1", repoPath, Set.of("jdk.CPULoad"), false);
            var subscriber = new LiveStreamingSubscriber(subscription,
                    new StreamingCallbacks(_ -> {}, () -> {}, _ -> {}));

            subscriber.start();
            Thread.sleep(500);

            assertDoesNotThrow(() -> {
                subscriber.close();
                subscriber.close();
            });
        }

        @Test
        void closeInvokesOnCloseCallbackOnce() throws Exception {
            var closeCount = new AtomicInteger(0);
            var closeLatch = new CountDownLatch(1);

            var subscription = new LiveStreamSubscription(
                    "session-1", repoPath, Set.of("jdk.CPULoad"), false);
            var subscriber = new LiveStreamingSubscriber(subscription,
                    new StreamingCallbacks(_ -> {}, () -> {}, _ -> {}, () -> {
                        closeCount.incrementAndGet();
                        closeLatch.countDown();
                    }));

            subscriber.start();
            Thread.sleep(500);
            subscriber.close();
            subscriber.close();

            assertTrue(closeLatch.await(5, TimeUnit.SECONDS));
            assertEquals(1, closeCount.get(), "onClose should be invoked exactly once");
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void closeWithoutStartIsNoOp() {
            var subscription = new LiveStreamSubscription(
                    "session-1", repoPath, Set.of("jdk.CPULoad"), false);
            var subscriber = new LiveStreamingSubscriber(subscription,
                    new StreamingCallbacks(_ -> {}, () -> {}, _ -> {}));

            // Close without calling start — eventStream is null, should be safe
            assertDoesNotThrow(subscriber::close);
        }
    }
}
