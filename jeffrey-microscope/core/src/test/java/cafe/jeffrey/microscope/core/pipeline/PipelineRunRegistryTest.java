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

package cafe.jeffrey.microscope.core.pipeline;

import cafe.jeffrey.microscope.common.exception.ErrorCode;
import cafe.jeffrey.microscope.common.exception.ErrorType;
import cafe.jeffrey.microscope.common.exception.JeffreyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

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

    @Test
    void rejectedSubmissionTerminatesJoinersAndLeavesResourcesWithTheCaller() {
        AtomicReference<PipelineRunRegistry<String>> registryRef = new AtomicReference<>();
        AtomicInteger submissions = new AtomicInteger();
        AtomicInteger bodies = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();
        AtomicReference<Boolean> joinedStarted = new AtomicReference<>();
        var failure = new RejectedExecutionException("scheduler stopped");
        var registry = new PipelineRunRegistry<String>(DEFINITION, PipelineRunOptions.unbounded(), CLOCK, task -> {
            if (submissions.incrementAndGet() == 1) {
                // Another caller can join the attempt between its registration and scheduling.
                var joined = registryRef.get().startOrJoin(PipelineRunRequest.of("profile-1", _ -> bodies.incrementAndGet()), false);
                joinedStarted.set(joined.started());
                throw failure;
            }
            task.run();
        });
        registryRef.set(registry);
        var request = new PipelineRunRequest<String>("profile-1", "", _ -> bodies.incrementAndGet(),
                _ -> completions.incrementAndGet());

        assertSame(failure, assertThrows(RejectedExecutionException.class, () -> registry.start(request)));
        assertFalse(joinedStarted.get(), "a joiner of the doomed attempt must not be told it started a run");
        // The key is released rather than left holding a stranded run.
        assertFalse(registry.isRunning("profile-1"));
        assertEquals(PipelineState.IDLE, registry.progress("profile-1").state());
        assertEquals(0, bodies.get());
        assertEquals(0, completions.get(), "a rejected start leaves cleanup to the caller");

        assertTrue(registry.startOrJoin(request, false).started(), "rejected submissions must not strand the key");
        assertEquals(1, bodies.get());
        assertEquals(1, completions.get());
        assertEquals(PipelineState.COMPLETED, registry.progress("profile-1").state());
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

        /**
         * A caller that only wants to inspect must not restart what already completed: the registry
         * used to keep a failed run and replace a completed one, so "omit retry to inspect without
         * restarting" held for a failure and rebuilt a success.
         */
        @Test
        @DisplayName("keeps a completed run unless the caller explicitly retries")
        void keepsACompletedRunWithoutAnExplicitRetry() {
            PipelineRunRegistry<String> registry = unbounded();
            AtomicInteger bodies = new AtomicInteger();
            PipelineRunRequest<String> request = PipelineRunRequest.of("profile-1",
                    run -> run.runStage("first", bodies::incrementAndGet));

            PipelineRunRegistry.StartResult first = registry.startOrJoin(request, false);
            assertTrue(first.started());
            await().atMost(5, SECONDS).untilAsserted(() -> assertFalse(registry.isRunning("profile-1")));
            assertEquals(PipelineState.COMPLETED, registry.progress("profile-1").state());

            PipelineRunRegistry.StartResult inspected = registry.startOrJoin(request, false);
            assertFalse(inspected.started(), "an inspection must not restart a completed run");
            assertEquals(1, bodies.get());

            PipelineRunRegistry.StartResult retried = registry.startOrJoin(request, true);
            assertTrue(retried.started(), "an explicit retry is what restarts a finished run");
            await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(2, bodies.get()));
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
                        ErrorType.CLIENT, ErrorCode.INVALID_REQUEST, "bad input");
            }), stored::set));

            await().atMost(5, SECONDS).untilAsserted(() -> assertNotNull(stored.get()));
            PipelineRunResult result = stored.get();
            assertEquals(PipelineState.FAILED, result.state());
            assertEquals(ErrorCode.INVALID_REQUEST.name(), result.errorCode());
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
