/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
            registry.register(OperationKind.RECORDING_ANALYSIS, first, value -> value, () -> "recording-a");
            install.countDown();
            assertTrue(installed.await(5, TimeUnit.SECONDS));
            var second = jobs.startOrJoin("second", false, value -> true, () -> "second-result");
            jobs.awaitWithin(second, Duration.ofSeconds(5));
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                String id = registry.register(OperationKind.RECORDING_ANALYSIS, second, value -> value, () -> "recording-b");
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
        registry.register(OperationKind.RECORDING_ANALYSIS, completed("a", now), value -> value, () -> "recording");
        registry.register(OperationKind.RECORDING_ANALYSIS, completed("b", now), value -> value, () -> "recording");
        assertEquals("b", registry.latestForRecording("recording").orElseThrow());
        registry.register(OperationKind.RECORDING_ANALYSIS, completed("a", now), value -> value, () -> "recording");
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
