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

import cafe.jeffrey.hub.core.streaming.ReplayStreamSubscription;
import cafe.jeffrey.hub.core.streaming.StreamingWindow;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import cafe.jeffrey.shared.common.Json;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

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
            await().atMost(10, TimeUnit.SECONDS).until(() -> !Json.toTree(service.status(ref(id), "events", 20)).path("finishedAt").isNull());
            var result = Json.toTree(service.status(ref(id), "types", 20));
            assertTrue(result.path("complete").asBoolean(), result.toString());
            assertEquals(1502, result.path("summary").path("totalEvents").asLong());
            assertEquals(2, result.path("summary").path("distinctEventTypes").asInt());
            assertEquals(1501, result.path("summary").path("buckets").get(0).path("eventTypes").get(0).path("count").asLong());
            assertEquals("completed", Json.toTree(service.cancel(ref(id))).path("status").asText());
        }
        try (var service = new HubActivityService(req -> subscription(req, List.of(file, corrupt), temp))) {
            String id = service.start(request());
            await().atMost(10, TimeUnit.SECONDS).until(() -> !Json.toTree(service.status(ref(id), "events", 20)).path("finishedAt").isNull());
            var result = Json.toTree(service.status(ref(id), "events", 20));
            assertFalse(result.path("complete").asBoolean());
            assertTrue(result.path("coverageKnown").asBoolean());
            assertEquals(1, result.path("sourceErrors").asInt());
            assertEquals(1502, result.path("summary").path("totalEvents").asLong());
        }
        try (var files = Files.list(temp.resolve("scratch"))) { assertEquals(0, files.count()); }
        assertTrue(Files.exists(file));
        assertEquals("not JFR", Files.readString(corrupt));
    }

    @Test
    void cancelledQueuedScanNeverOpensItsSourceAndQueueIsBounded() {
        List<Runnable> queued = new ArrayList<>();
        AtomicInteger sources = new AtomicInteger();
        try (var service = new HubActivityService(req -> { sources.incrementAndGet(); throw new AssertionError(); },
                queued::add, Clock.systemUTC())) {
            String first = service.start(request());
            for (int i = 1; i < 16; i++) {
                service.start(request());
            }
            assertThrows(IllegalStateException.class, () -> service.start(request()));
            assertEquals("cancel_requested", Json.toTree(service.cancel(ref(first))).path("status").asText());
            queued.get(0).run();
            assertEquals("cancelled", Json.toTree(service.status(ref(first), "events", 20)).path("status").asText());
            assertFalse(Json.toTree(service.status(ref(first), "events", 20)).path("complete").asBoolean());
            service.start(request()); // A finished job can be evicted to admit new work.
            assertEquals(0, sources.get());
        }
    }

    @Test
    void cancellationDuringSourceResolutionTerminatesTheWorker() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        try (var service = new HubActivityService(req -> {
            entered.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException e) { throw new IllegalStateException(e); }
            throw new AssertionError();
        })) {
            String id = service.start(request());
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            service.cancel(ref(id));
            await().atMost(5, TimeUnit.SECONDS).until(() -> !Json.toTree(service.status(ref(id), "events", 20)).path("finishedAt").isNull());
            assertEquals("cancelled", Json.toTree(service.status(ref(id), "events", 20)).path("status").asText());
        }
    }

    @Test
    void onlyTwoReadersRunAndCancellationReleasesAQueuedScan() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        AtomicInteger entered = new AtomicInteger();
        try (var service = new HubActivityService(req -> {
            maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
            entered.incrementAndGet();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException e) { throw new IllegalStateException(e); }
            finally { active.decrementAndGet(); }
            throw new AssertionError();
        })) {
            String first = service.start(request());
            service.start(request());
            await().atMost(5, TimeUnit.SECONDS).until(() -> entered.get() == 2);
            String third = service.start(request());
            assertEquals("queued", Json.toTree(service.status(ref(third), "events", 20)).path("status").asText());
            service.cancel(ref(first));
            await().atMost(5, TimeUnit.SECONDS).until(() -> entered.get() == 3);
            assertEquals(2, maximum.get());
        }
        await().atMost(5, TimeUnit.SECONDS).until(() -> active.get() == 0);
    }

    @Test
    void rejectedSchedulingDoesNotConsumeTheJobCapacity() {
        try (var service = new HubActivityService(req -> { throw new AssertionError(); },
                _ -> { throw new RejectedExecutionException("closed"); }, Clock.systemUTC())) {
            for (int i = 0; i < 20; i++) {
                assertThrows(RejectedExecutionException.class, () -> service.start(request()));
            }
        }
    }

    private static ActivityScanRef ref(String id) {
        return new ActivityScanRef("workspace", "project", "session", id);
    }

    private static ReplayStreamSubscription subscription(ActivityRequest req, List<Path> files, Path temp) {
        return new ReplayStreamSubscription(req.sessionId(), files, req.eventTypes(),
                new StreamingWindow(Instant.ofEpochMilli(req.startTime()), Instant.ofEpochMilli(req.endTime())),
                temp.resolve("scratch"), req.workspaceId(), req.projectId());
    }
}
