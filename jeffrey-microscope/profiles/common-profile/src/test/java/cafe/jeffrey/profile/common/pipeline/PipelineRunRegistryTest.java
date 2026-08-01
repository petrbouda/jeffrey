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

import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.ErrorType;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineRunRegistryTest {

    private static final PipelineDefinition DEFINITION =
            new PipelineDefinition("test", List.of("first", "second"));

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private static PipelineRunRegistry<String> unbounded() {
        return new PipelineRunRegistry<>(DEFINITION, PipelineRunOptions.unbounded(), CLOCK);
    }

    /** Blocks the pipeline body until released, so a run can be inspected while it is genuinely in flight. */
    private static PipelineRunRequest<String> blockingRun(
            String key, CountDownLatch entered, CountDownLatch release) {

        return PipelineRunRequest.of(key, run -> run.runStage("first", () -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", e);
            }
        }));
    }

    @Nested
    @DisplayName("One run per key")
    class OneRunPerKey {

        @Test
        @DisplayName("refuses a second run for a key that is already running, instead of racing it")
        void refusesConcurrentRunForSameKey() throws InterruptedException {
            PipelineRunRegistry<String> registry = unbounded();
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger bodies = new AtomicInteger();

            assertTrue(registry.start(blockingRun("profile-1", entered, release)));
            assertTrue(entered.await(5, TimeUnit.SECONDS), "the first run must start");

            assertFalse(registry.start(PipelineRunRequest.of("profile-1", _ -> bodies.incrementAndGet())));
            assertTrue(registry.isRunning("profile-1"));

            release.countDown();
            await().atMost(5, SECONDS).untilAsserted(() -> assertFalse(registry.isRunning("profile-1")));
            assertEquals(0, bodies.get(), "the refused request must not have run anything");
        }

        @Test
        @DisplayName("keeps different keys independent")
        void allowsConcurrentRunsForDifferentKeys() throws InterruptedException {
            PipelineRunRegistry<String> registry = unbounded();
            CountDownLatch entered = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);

            assertTrue(registry.start(blockingRun("profile-1", entered, release)));
            assertTrue(registry.start(blockingRun("profile-2", entered, release)));

            assertTrue(entered.await(5, TimeUnit.SECONDS), "both runs must start");

            release.countDown();
            await().atMost(5, SECONDS).untilAsserted(() -> {
                assertFalse(registry.isRunning("profile-1"));
                assertFalse(registry.isRunning("profile-2"));
            });
        }

        @Test
        @DisplayName("starts a fresh run once the previous one for the key has finished")
        void allowsRestartAfterCompletion() {
            PipelineRunRegistry<String> registry = unbounded();

            assertTrue(registry.start(PipelineRunRequest.of("profile-1", run -> run.runStage("first", () -> {
            }))));
            await().atMost(5, SECONDS).untilAsserted(() -> assertFalse(registry.isRunning("profile-1")));

            assertTrue(registry.start(PipelineRunRequest.of("profile-1", run -> run.runStage("first", () -> {
            }))));
        }

        @Test
        @DisplayName("reports idle for a key that has never run")
        void reportsIdleForUnknownKey() {
            PipelineProgress progress = unbounded().progress("nothing-here");

            assertEquals(PipelineState.IDLE, progress.state());
            assertEquals("test", progress.pipelineId());
        }
    }

    @Nested
    @DisplayName("Concurrency ceiling")
    class Ceiling {

        @Test
        @DisplayName("holds a run at the gate until a slot frees, rather than rejecting it")
        void queuesBeyondTheCeiling() throws InterruptedException {
            PipelineRunRegistry<String> registry = new PipelineRunRegistry<>(
                    DEFINITION, PipelineRunOptions.bounded(1, Duration.ofMinutes(15)), CLOCK);

            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch secondEntered = new CountDownLatch(1);

            assertTrue(registry.start(blockingRun("profile-1", firstEntered, releaseFirst)));
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));

            // Accepted immediately — the ceiling delays execution, it does not refuse the request.
            assertTrue(registry.start(PipelineRunRequest.of(
                    "profile-2", run -> run.runStage("first", secondEntered::countDown))));
            assertFalse(secondEntered.await(300, TimeUnit.MILLISECONDS),
                    "the second run must wait for the only slot");

            releaseFirst.countDown();
            assertTrue(secondEntered.await(5, TimeUnit.SECONDS),
                    "the second run must proceed once the slot frees");
        }
    }

    @Nested
    @DisplayName("Cancellation")
    class Cancellation {

        @Test
        @DisplayName("marks a cancelled run failed and interrupts the work, which unblocks immediately")
        void cancelMarksTheRunFailed() throws InterruptedException {
            PipelineRunRegistry<String> registry = unbounded();
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicReference<PipelineRunResult> stored = new AtomicReference<>();

            registry.start(new PipelineRunRequest<>("profile-1", "",
                    blockingRun("profile-1", entered, release).work(), stored::set));
            assertTrue(entered.await(5, TimeUnit.SECONDS));

            assertTrue(registry.cancel("profile-1"));

            assertEquals(PipelineState.FAILED, registry.progress("profile-1").state());
            assertFalse(registry.isRunning("profile-1"));
            // The interrupt reaches the blocked work — no need to release the latch — and the
            // terminal result delivered to the caller is the cancellation, not a late completion.
            await().atMost(5, SECONDS).untilAsserted(() -> assertNotNull(stored.get()));
            assertEquals(PipelineState.FAILED, stored.get().state());
            assertEquals(PipelineState.FAILED, registry.progress("profile-1").state());
        }

        @Test
        @DisplayName("stays cancelled even when the work ignores the interrupt and returns normally")
        void zombieCompletionCannotOverturnCancellation() throws InterruptedException {
            PipelineRunRegistry<String> registry = unbounded();
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch cancelled = new CountDownLatch(1);
            AtomicReference<PipelineRunResult> stored = new AtomicReference<>();

            // Work that swallows the interrupt and finishes as if nothing happened — the worst case
            // for cancellation, because the registry then tries to complete the run.
            registry.start(new PipelineRunRequest<>("profile-1", "", run -> run.runStage("first", () -> {
                entered.countDown();
                try {
                    cancelled.await();
                } catch (InterruptedException e) {
                    // Deliberately ignored: simulates non-interruptible work running to completion.
                }
            }), stored::set));
            assertTrue(entered.await(5, TimeUnit.SECONDS));

            assertTrue(registry.cancel("profile-1"));
            cancelled.countDown();

            await().atMost(5, SECONDS).untilAsserted(() -> assertNotNull(stored.get()));
            assertEquals(PipelineState.FAILED, stored.get().state(),
                    "the first terminal transition (the cancel) must win");
            assertEquals(PipelineState.FAILED, registry.progress("profile-1").state());
        }

        @Test
        @DisplayName("a run cancelled while queued for a slot never executes its body")
        void cancelledQueuedRunNeverExecutes() throws InterruptedException {
            PipelineRunRegistry<String> registry = new PipelineRunRegistry<>(
                    DEFINITION, PipelineRunOptions.bounded(1, Duration.ofMinutes(15)), CLOCK);
            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            AtomicInteger secondBodyRuns = new AtomicInteger();
            AtomicReference<PipelineRunResult> secondResult = new AtomicReference<>();

            assertTrue(registry.start(blockingRun("profile-1", firstEntered, releaseFirst)));
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));

            assertTrue(registry.start(new PipelineRunRequest<>("profile-2", "",
                    run -> run.runStage("first", secondBodyRuns::incrementAndGet),
                    secondResult::set)));

            assertTrue(registry.cancel("profile-2"));
            releaseFirst.countDown();

            await().atMost(5, SECONDS).untilAsserted(() -> assertNotNull(secondResult.get()));
            assertEquals(PipelineState.FAILED, secondResult.get().state());
            assertEquals(0, secondBodyRuns.get(), "a cancelled queued run must not start its work");
        }

        @Test
        @DisplayName("has nothing to cancel when no run is in flight")
        void cancelIsANoOpWithoutARun() {
            assertFalse(unbounded().cancel("profile-1"));
        }
    }

    @Nested
    @DisplayName("Terminal result")
    class TerminalResult {

        @Test
        @DisplayName("hands the completed result to the caller so it can be stored")
        void notifiesOnCompletion() {
            PipelineRunRegistry<String> registry = unbounded();
            AtomicReference<PipelineRunResult> stored = new AtomicReference<>();

            registry.start(new PipelineRunRequest<>("profile-1", "cpu", run -> {
                run.runStage("first", () -> {
                });
                run.skipStage("second");
            }, stored::set));

            await().atMost(5, SECONDS).untilAsserted(() -> assertNotNull(stored.get()));
            PipelineRunResult result = stored.get();
            assertEquals(PipelineState.COMPLETED, result.state());
            assertEquals("cpu", result.scopeId(), "the scope must travel to the stored run");
            assertEquals(2, result.totalSteps());
            assertEquals(2, result.completedSteps());
        }

        @Test
        @DisplayName("carries a Jeffrey error code through, so the frontend can react to the failure")
        void notifiesOnFailureWithCode() {
            PipelineRunRegistry<String> registry = unbounded();
            AtomicReference<PipelineRunResult> stored = new AtomicReference<>();

            registry.start(new PipelineRunRequest<>("profile-1", "", run -> run.runStage("first", () -> {
                throw new JeffreyException(
                        ErrorType.CLIENT, ErrorCode.HEAP_DUMP_NEEDS_SANITIZATION, "needs repair");
            }), stored::set));

            await().atMost(5, SECONDS).untilAsserted(() -> assertNotNull(stored.get()));
            PipelineRunResult result = stored.get();
            assertEquals(PipelineState.FAILED, result.state());
            assertEquals(ErrorCode.HEAP_DUMP_NEEDS_SANITIZATION.name(), result.errorCode());
            assertEquals(StageStatus.FAILED, result.stages().getFirst().status());
        }

        @Test
        @DisplayName("keeps a run that completed even though storing its result blew up")
        void survivesAFailingListener() {
            PipelineRunRegistry<String> registry = unbounded();

            registry.start(new PipelineRunRequest<>("profile-1", "", run -> run.runStage("first", () -> {
            }), _ -> {
                throw new IllegalStateException("the database is gone");
            }));

            await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(
                    PipelineState.COMPLETED, registry.progress("profile-1").state()));
        }
    }
}
