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
package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.profile.common.operation.OperationHandle;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationsMcpToolsTest {

    @Test
    void cancellationReachesTheWorkerWhileItsProgressReadIsBlocked() throws Exception {
        BoundedJobs<String, String> jobs = new BoundedJobs<>(Duration.ofMillis(10));
        McpOperationRegistry registry = new McpOperationRegistry();
        CountDownLatch workerEntered = new CountDownLatch(1);
        CountDownLatch progressEntered = new CountDownLatch(1);
        CountDownLatch releaseProgress = new CountDownLatch(1);
        CountDownLatch workerInterrupted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        OperationHandle<String> operation = jobs.startOrJoin("blocked-progress", false, value -> true, control -> {
            control.progress((Supplier<Object>) () -> {
                progressEntered.countDown();
                while (releaseProgress.getCount() != 0) {
                    try {
                        releaseProgress.await();
                    } catch (InterruptedException ignored) {
                        // Simulates a status query which cannot be interrupted immediately.
                    }
                }
                return Map.of("stage", "copy");
            });
            workerEntered.countDown();
            try {
                releaseWorker.await();
            } catch (InterruptedException e) {
                workerInterrupted.countDown();
                control.checkCancellation();
            }
            return "result";
        });
        String id = registry.register("recording_analysis", operation, value -> value);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            assertTrue(workerEntered.await(5, TimeUnit.SECONDS));
            Future<?> status = callers.submit(() -> registry.status(id));
            assertTrue(progressEntered.await(5, TimeUnit.SECONDS));
            Future<?> cancellation = callers.submit(() -> registry.cancel(id, kind -> true));
            assertTrue(workerInterrupted.await(1, TimeUnit.SECONDS), "slow progress must not hold the cancellation lock");
            cancellation.get(1, TimeUnit.SECONDS);
            releaseProgress.countDown();
            status.get(5, TimeUnit.SECONDS);
        } finally {
            releaseProgress.countDown();
            releaseWorker.countDown();
            callers.shutdownNow();
            assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void pendingCancellationKeepsTheExactAttemptUntilItFinishesAndNeverCancelsARetry() throws Exception {
        BoundedJobs<String, String> jobs = new BoundedJobs<>(Duration.ofMillis(10));
        McpOperationRegistry registry = new McpOperationRegistry();
        OperationsMcpTools tools = new OperationsMcpTools(registry);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger workCount = new AtomicInteger();
        OperationHandle<String> first = jobs.startOrJoin("session", false, value -> true, control -> {
            workCount.incrementAndGet();
            entered.countDown();
            while (release.getCount() != 0) {
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                    // Simulates an operation that cannot stop until the current stage finishes.
                }
            }
            control.checkCancellation();
            return "copy";
        });
        String firstId = registry.register("hub_download", first, value -> Map.of("recordingId", value));
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            JsonNode cancelled = Json.mapper().readTree(tools.cancel(firstId));
            assertEquals("cancel_requested", cancelled.path("status").asString());
            assertTrue(cancelled.path("finishedAt").isNull());
            assertFalse(cancelled.path("retryable").asBoolean());
            assertEquals(firstId, jobs.startOrJoin("session", true, value -> true, () -> "rival")
                    .snapshot().operationId());
            assertEquals("cancel_requested", Json.mapper().readTree(tools.status(firstId)).path("status").asString());
            assertEquals(1, workCount.get());
        } finally {
            release.countDown();
        }
        await().atMost(5, TimeUnit.SECONDS).until(() -> first.snapshot().state().terminal());
        OperationHandle<String> retry = jobs.startOrJoin("session", true, value -> true, () -> "new-copy");
        String retryId = registry.register("hub_download", retry, value -> Map.of("recordingId", value));
        assertFalse(firstId.equals(retryId));
        assertEquals("cancelled", Json.mapper().readTree(tools.cancel(firstId)).path("status").asString());
        await().atMost(5, TimeUnit.SECONDS).until(() -> retry.snapshot().state().terminal());
        assertEquals("completed", registry.status(retryId).status());
        assertEquals("new-copy", Json.mapper().readTree(tools.status(retryId)).path("result").path("recordingId").asString());
    }

    @Test
    void appliesOriginatingFamilyGatesToBothStatusAndCancellation() {
        BoundedJobs<String, String> jobs = new BoundedJobs<>();
        McpOperationRegistry registry = new McpOperationRegistry();
        OperationHandle<String> finished = jobs.rememberCompleted("s", "r");
        String id = registry.register("hub_download", finished, value -> value);
        OperationsMcpTools hidden = new OperationsMcpTools(registry, kind -> !kind.equals("hub_download"));
        assertThrows(IllegalArgumentException.class, () -> hidden.status(id));
        assertThrows(IllegalArgumentException.class, () -> hidden.cancel(id));
        assertEquals("completed", registry.status(id).status());
    }

    @Test
    void expiredIdsNeverStartReplacementWork() {
        MutableClock clock = new MutableClock();
        BoundedJobs<String, String> jobs = new BoundedJobs<>(Duration.ofSeconds(1), Duration.ofHours(1), clock);
        McpOperationRegistry registry = new McpOperationRegistry(clock);
        String id = registry.register("recording_analysis", jobs.rememberCompleted("r", "p"), value -> value);
        clock.now = clock.now.plus(Duration.ofHours(2));
        assertThrows(IllegalArgumentException.class, () -> registry.status(id));
        assertThrows(IllegalArgumentException.class, () -> registry.cancel(id, kind -> true));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-12T12:00:00Z");
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }
        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
        @Override
        public Instant instant() {
            return now;
        }
    }
}
