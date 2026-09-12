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

package cafe.jeffrey.profile.common.pipeline;

import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.common.operation.OperationState;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PipelineCancellationOwnershipTest {

    @Test
    void acknowledgedInterruptionIsReportedAsCancelled() throws Exception {
        PipelineRunRegistry<String> registry = new PipelineRunRegistry<>(
                new PipelineDefinition("test", List.of("work")), PipelineRunOptions.unbounded(), Clock.systemUTC());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var operation = registry.startOrJoin(PipelineRunRequest.of("p", run -> {
            entered.countDown();
            waitFor(release);
        }), false).operation();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTrue(operation.cancel());
            await().atMost(5, TimeUnit.SECONDS).until(() -> operation.snapshot().state().terminal());
            assertEquals(OperationState.CANCELLED, operation.snapshot().state());
        } finally {
            release.countDown();
        }
    }

    @Test
    void anAttemptWaitingForAConcurrencySlotReportsQueued() throws Exception {
        PipelineRunRegistry<String> registry = new PipelineRunRegistry<>(
                new PipelineDefinition("test", List.of("work")),
                PipelineRunOptions.bounded(1, Duration.ofHours(1)), Clock.systemUTC());
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        registry.start(PipelineRunRequest.of("first", run -> {
            firstEntered.countDown();
            waitFor(releaseFirst);
        }));
        try {
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
            var second = registry.startOrJoin(PipelineRunRequest.of("second", run -> {
                secondEntered.countDown();
                waitFor(releaseSecond);
            }), false).operation();
            await().during(Duration.ofMillis(100)).atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertEquals(OperationState.QUEUED, second.snapshot().state()));
            releaseFirst.countDown();
            assertTrue(secondEntered.await(5, TimeUnit.SECONDS));
            assertEquals(OperationState.RUNNING, second.snapshot().state());
        } finally {
            releaseFirst.countDown();
            releaseSecond.countDown();
        }
    }

    private static void waitFor(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aSuccessfulFinalStageRemainsCompletedAfterACancellationRequest() throws Exception {
        PipelineRunRegistry<String> registry = new PipelineRunRegistry<>(
                new PipelineDefinition("test", List.of("work")), PipelineRunOptions.unbounded(), Clock.systemUTC());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var operation = registry.startOrJoin(PipelineRunRequest.of("p", run -> run.runStage("work", () -> {
            entered.countDown();
            while (release.getCount() != 0) {
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                    // The final stage succeeds even though it could not stop midway.
                }
            }
        })), false).operation();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTrue(operation.cancel());
        } finally {
            release.countDown();
        }
        await().atMost(5, TimeUnit.SECONDS).until(() -> operation.snapshot().state().terminal());
        assertEquals(OperationState.COMPLETED, operation.snapshot().state());
        assertTrue(operation.snapshot().cancellationRequested());
    }

    @Test
    void cancelledWorkerKeepsItsKeyUntilWorkAndCleanupEnd() throws InterruptedException {
        PipelineDefinition definition = new PipelineDefinition("test", List.of("work"));
        PipelineRunRegistry<String> registry = new PipelineRunRegistry<>(
                definition, PipelineRunOptions.unbounded(), Clock.systemUTC());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch cleanupEntered = new CountDownLatch(1);
        CountDownLatch cleanupRelease = new CountDownLatch(1);
        AtomicInteger rivals = new AtomicInteger();
        try {
            registry.start(new PipelineRunRequest<>("p", "", run -> {
                entered.countDown();
                while (release.getCount() != 0) {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        interrupted.countDown();
                    }
                }
            }, result -> {
                cleanupEntered.countDown();
                try {
                    cleanupRelease.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTrue(registry.cancel("p"));
            assertTrue(interrupted.await(5, TimeUnit.SECONDS));
            assertTrue(registry.isRunning("p"), "cancellation is pending while the worker is alive");
            assertFalse(registry.start(new PipelineRunRequest<>("p", "", run -> rivals.incrementAndGet(), null)));
            release.countDown();
            assertTrue(cleanupEntered.await(5, TimeUnit.SECONDS));
            assertTrue(registry.isRunning("p"), "cleanup still owns the key");
            assertFalse(registry.start(new PipelineRunRequest<>("p", "", run -> rivals.incrementAndGet(), null)));
            assertEquals(0, rivals.get());
        } finally {
            release.countDown();
            cleanupRelease.countDown();
        }
        await().atMost(5, TimeUnit.SECONDS).until(() -> !registry.isRunning("p"));
    }
}
