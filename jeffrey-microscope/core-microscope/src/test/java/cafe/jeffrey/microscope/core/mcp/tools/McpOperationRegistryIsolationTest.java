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
import cafe.jeffrey.profile.common.operation.OperationSnapshot;
import cafe.jeffrey.profile.common.operation.OperationState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpOperationRegistryIsolationTest {
    @Test
    void unrelatedSlowProgressCannotBlockRegistrationStatusCancellationOrRecordingLookup() throws Exception {
        McpOperationRegistry registry = new McpOperationRegistry();
        BoundedJobs<String, String> jobs = new BoundedJobs<>();
        CountDownLatch install = new CountDownLatch(1);
        CountDownLatch installed = new CountDownLatch(1);
        CountDownLatch releaseProgress = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicInteger progressReads = new AtomicInteger();
        var first = jobs.startOrJoin("first", false, value -> true, control -> {
            waitFor(install);
            control.progress((Supplier<Object>) () -> {
                progressReads.incrementAndGet();
                waitFor(releaseProgress);
                return "progress";
            });
            installed.countDown();
            waitFor(releaseWorker);
            return "first-result";
        });
        try {
            registry.register("recording_analysis", first, value -> value, () -> "recording-a");
            install.countDown();
            assertTrue(installed.await(5, TimeUnit.SECONDS));
            var second = jobs.startOrJoin("second", false, value -> true, () -> "second-result");
            jobs.awaitWithin(second, Duration.ofSeconds(5));
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                String id = registry.register("recording_analysis", second, value -> value, () -> "recording-b");
                assertEquals("completed", registry.status(id).status());
                assertEquals("completed", registry.cancel(id, kind -> true).status());
                assertEquals(id, registry.latestForRecording("recording-b").orElseThrow());
            });
            assertEquals(0, progressReads.get(), "Unrelated operation controls must not evaluate progress");
        } finally {
            install.countDown();
            releaseProgress.countDown();
            releaseWorker.countDown();
            jobs.awaitWithin(first, Duration.ofSeconds(5));
        }
    }

    @Test
    void latestRecordingAttemptUsesRegistrationOrderWhenTimestampsMatch() {
        Instant now = Instant.parse("2026-09-12T12:00:00Z");
        McpOperationRegistry registry = new McpOperationRegistry(Clock.fixed(now, ZoneOffset.UTC));
        registry.register("recording_analysis", completed("a", now), value -> value, () -> "recording");
        registry.register("recording_analysis", completed("b", now), value -> value, () -> "recording");
        assertEquals("b", registry.latestForRecording("recording").orElseThrow());
        registry.register("recording_analysis", completed("a", now), value -> value, () -> "recording");
        assertEquals("b", registry.latestForRecording("recording").orElseThrow());
    }

    private static OperationHandle<String> completed(String id, Instant now) {
        return new OperationHandle<>() {
            @Override
            public OperationSnapshot<String> snapshot() {
                return new OperationSnapshot<>(id, OperationState.COMPLETED, now, now,
                        false, "completed", null, "result", null);
            }

            @Override
            public boolean cancel() {
                return false;
            }
        };
    }

    private static void waitFor(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
