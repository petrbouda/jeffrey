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

package cafe.jeffrey.hub.core.activity;

import cafe.jeffrey.hub.core.streaming.ReplayScopeNotFoundException;
import cafe.jeffrey.hub.core.streaming.ReplayStreamSubscription;
import cafe.jeffrey.hub.core.streaming.StreamingCallbacks;
import cafe.jeffrey.hub.core.streaming.StreamingWindow;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.activity.ActivityLimits;
import cafe.jeffrey.shared.common.activity.ActivityOrder;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class HubActivityServiceTest {
    @Name("test.ActivityA")
    static class ActivityA extends Event {
        String message = "large field".repeat(100);
    }
    @Name("test.ActivityB")
    static class ActivityB extends Event {}

    private static ActivityRequest request() {
        long start = Clock.systemUTC().instant().minusSeconds(60).toEpochMilli();
        return new ActivityRequest("workspace", "project", "session", start, start + 120000, 60000, Set.of());
    }

    @Test
    void scansBeyondTheRawMcpRowLimitAndReportsCorruptFiles(@TempDir Path temp) throws Exception {
        Path file = temp.resolve("recording.jfr");
        try (Recording recording = new Recording()) {
            recording.enable(ActivityA.class);
            recording.enable(ActivityB.class);
            recording.start();
            for (int i = 0; i < 1501; i++) {
                new ActivityA().commit();
            }
            new ActivityB().commit();
            recording.stop();
            recording.dump(file);
        }
        Path corrupt = Files.writeString(temp.resolve("corrupt.jfr"), "not JFR");
        try (var service = new HubActivityService(req -> subscription(req, List.of(file), temp))) {
            String id = service.start(request());
            awaitFinished(service, id);
            var result = Json.toTree(service.status(ref(id), ActivityOrder.TYPES, 20, 0));
            assertTrue(result.path("complete").asBoolean(), result.toString());
            assertEquals(1502, result.path("summary").path("totalEvents").asLong());
            assertEquals(2, result.path("summary").path("distinctEventTypes").asInt());
            assertEquals(1, result.path("filesTotal").asInt());
            assertEquals(1501,
                    result.path("summary").path("buckets").get(0).path("eventTypes").get(0).path("count").asLong());
            assertEquals("completed", Json.toTree(service.cancel(ref(id))).path("status").asText());
        }
        try (var service = new HubActivityService(req -> subscription(req, List.of(file, corrupt), temp))) {
            String id = service.start(request());
            awaitFinished(service, id);
            var result = Json.toTree(service.status(ref(id), ActivityOrder.EVENTS, 20, 0));
            assertFalse(result.path("complete").asBoolean());
            assertTrue(result.path("coverageKnown").asBoolean());
            assertEquals(1, result.path("sourceErrors").asInt());
            assertEquals(1502, result.path("summary").path("totalEvents").asLong());
        }
        try (var files = Files.list(temp.resolve("scratch"))) {
            assertEquals(0, files.count());
        }
        assertTrue(Files.exists(file));
        assertEquals("not JFR", Files.readString(corrupt));
    }

    /** An unknown scope is the caller's error, reported before a scan ID exists to poll. */
    @Test
    void unknownScopeFailsTheCallerAndRegistersNothing() {
        AtomicInteger resolutions = new AtomicInteger();
        try (var service = new HubActivityService(req -> {
            resolutions.incrementAndGet();
            throw new ReplayScopeNotFoundException("Session not found in requested project");
        })) {
            var error = assertThrows(ReplayScopeNotFoundException.class, () -> service.start(request()));
            assertEquals("Session not found in requested project", error.getMessage());
            assertEquals(1, resolutions.get());
            // Nothing was admitted, so the retained-scan capacity is untouched.
            for (int i = 0; i < 20; i++) {
                assertThrows(ReplayScopeNotFoundException.class, () -> service.start(request()));
            }
        }
    }

    /** Exhausting the event-type budget abandons the reader instead of letting the scan run on. */
    @Test
    void typeCapacityStopsTheReaderAndFailsTheScan(@TempDir Path temp) {
        var reader = new ControllableReader();
        try (var service = new HubActivityService(
                req -> subscription(req, List.of(), temp),
                reader.factory(),
                Schedulers.sharedVirtual(),
                Clock.systemUTC())) {
            String id = service.start(request());
            reader.awaitOpen();

            try {
                long timestamp = request().startTime();
                for (int i = 0; i < ActivityLimits.MAX_OBSERVED_TYPES; i++) {
                    reader.emit("Type" + i, timestamp);
                }
                assertFalse(reader.closed());
                reader.emit("OneTooMany", timestamp);
                assertTrue(reader.closed(), "the reader must be abandoned, not left counting");
            } finally {
                reader.finish();
            }
            awaitFinished(service, id);
            var result = Json.toTree(service.status(ref(id), ActivityOrder.EVENTS, 20, 0));
            assertEquals("failed", result.path("status").asText());
            assertTrue(result.path("error").asText().contains("eventTypes filter"), result.toString());
            // Nothing was counted past the budget, and the event that tripped it was not admitted.
            assertEquals(ActivityLimits.MAX_OBSERVED_TYPES, result.path("summary").path("totalEvents").asLong());
            assertEquals(ActivityLimits.MAX_OBSERVED_TYPES, result.path("summary").path("distinctEventTypes").asInt());
        }
    }

    @Test
    void cancelledQueuedScanNeverOpensAReaderAndQueueIsBounded(@TempDir Path temp) {
        List<Runnable> queued = new ArrayList<>();
        AtomicInteger resolutions = new AtomicInteger();
        try (var service = new HubActivityService(
                req -> {
                    resolutions.incrementAndGet();
                    return subscription(req, List.of(), temp);
                },
                queued::add,
                Clock.systemUTC())) {
            String first = service.start(request());
            for (int i = 1; i < 16; i++) {
                service.start(request());
            }
            assertThrows(IllegalStateException.class, () -> service.start(request()));
            assertEquals("cancel_requested", Json.toTree(service.cancel(ref(first))).path("status").asText());
            queued.get(0).run();
            assertEquals("cancelled",
                    Json.toTree(service.status(ref(first), ActivityOrder.EVENTS, 20, 0)).path("status").asText());
            assertFalse(Json.toTree(service.status(ref(first), ActivityOrder.EVENTS, 20, 0))
                    .path("complete").asBoolean());
            service.start(request()); // A finished job can be evicted to admit new work.
            // Once per start() attempt, on the caller's thread, never on the worker: sixteen that
            // were admitted, the one refused for capacity, and the one that evicted a finished scan.
            assertEquals(18, resolutions.get());
            assertFalse(Files.exists(temp.resolve("scratch")));
        }
    }

    @Test
    void onlyTwoReadersRunAndCancellationReleasesAQueuedScan(@TempDir Path temp) {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        AtomicInteger started = new AtomicInteger();
        ActivityReader.Factory blocking = (subscription, callbacks, events) -> new ActivityReader() {
            @Override
            public void start() {
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                started.incrementAndGet();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    active.decrementAndGet();
                    callbacks.onClose().run();
                }
            }

            @Override
            public void close() {
            }
        };
        try (var service = new HubActivityService(
                req -> subscription(req, List.of(), temp), blocking, Schedulers.sharedVirtual(), Clock.systemUTC())) {
            String first = service.start(request());
            service.start(request());
            await().atMost(5, TimeUnit.SECONDS).until(() -> started.get() == 2);
            String third = service.start(request());
            assertEquals("queued",
                    Json.toTree(service.status(ref(third), ActivityOrder.EVENTS, 20, 0)).path("status").asText());

            service.cancel(ref(first));
            await().atMost(5, TimeUnit.SECONDS).until(() -> started.get() == 3);
            assertEquals(2, maximum.get(), "a third reader must never run alongside two others");
        }
        await().atMost(5, TimeUnit.SECONDS).until(() -> active.get() == 0);
    }

    @Test
    void rejectedSchedulingDoesNotConsumeTheJobCapacity(@TempDir Path temp) {
        try (var service = new HubActivityService(
                req -> subscription(req, List.of(), temp),
                _ -> {
                    throw new RejectedExecutionException("closed");
                },
                Clock.systemUTC())) {
            for (int i = 0; i < 20; i++) {
                assertThrows(RejectedExecutionException.class, () -> service.start(request()));
            }
        }
    }

    private static void awaitFinished(HubActivityService service, String id) {
        await().atMost(10, TimeUnit.SECONDS).until(() -> !Json
                .toTree(service.status(ref(id), ActivityOrder.EVENTS, 20, 0)).path("finishedAt").isNull());
    }

    private static ActivityScanRef ref(String id) {
        return new ActivityScanRef("workspace", "project", "session", id);
    }

    private static ReplayStreamSubscription subscription(ActivityRequest req, List<Path> files, Path temp) {
        return new ReplayStreamSubscription(
                req.sessionId(),
                files,
                req.eventTypes(),
                new StreamingWindow(Instant.ofEpochMilli(req.startTime()), Instant.ofEpochMilli(req.endTime())),
                temp.resolve("scratch"),
                req.workspaceId(),
                req.projectId());
    }

    /** A reader the test starts, feeds one event at a time, and finishes on demand. */
    private static final class ControllableReader {

        private volatile BiConsumer<String, Instant> events;
        private volatile StreamingCallbacks callbacks;
        private final AtomicBoolean closed = new AtomicBoolean();

        private final CountDownLatch opened = new CountDownLatch(1);

        ActivityReader.Factory factory() {
            return (subscription, streamingCallbacks, consumer) -> {
                events = consumer;
                callbacks = streamingCallbacks;
                return new ActivityReader() {
                    @Override
                    public void start() {
                        opened.countDown();
                    }

                    @Override
                    public void close() {
                        closed.set(true);
                    }
                };
            };
        }

        void awaitOpen() {
            try {
                assertTrue(opened.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        void emit(String type, long timestamp) {
            events.accept(type, Instant.ofEpochMilli(timestamp));
        }

        boolean closed() {
            return closed.get();
        }

        /** Releases the worker that is parked on the reader's cleanup signal. */
        void finish() {
            callbacks.onClose().run();
        }
    }
}
