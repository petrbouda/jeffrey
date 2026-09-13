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
import cafe.jeffrey.profile.common.operation.OperationState;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What keeps a client from turning one import or one multi-gigabyte transfer into two: work is keyed
 * by what the caller already holds, and a second call for the same key joins the first rather than
 * starting a rival.
 */
class BoundedJobsTest {

    private static final Duration GENEROUS = Duration.ofSeconds(10);
    private static final Duration IMMEDIATE = Duration.ofMillis(50);

    @Test
    void cancelledAttemptKeepsItsIdentityAndKeyUntilTheWorkerExits() throws InterruptedException {
        BoundedJobs<String, String> jobs = new BoundedJobs<>(IMMEDIATE);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        try {
            OperationHandle<String> first = jobs.startOrJoin("key", false, value -> true, control -> {
                attempts.incrementAndGet();
                entered.countDown();
                while (release.getCount() != 0) {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        interrupted.countDown();
                    }
                }
                control.checkCancellation();
                return "result";
            });
            assertTrue(entered.await(5, SECONDS));
            assertTrue(first.cancel());
            assertTrue(interrupted.await(5, SECONDS));
            assertEquals(OperationState.CANCEL_REQUESTED, first.snapshot().state());
            OperationHandle<String> joined = jobs.startOrJoin("key", true, value -> true, () -> "rival");
            assertEquals(first.snapshot().operationId(), joined.snapshot().operationId());
            assertEquals(1, attempts.get());
            release.countDown();
            await().atMost(5, SECONDS).until(() -> first.snapshot().state().terminal());
            OperationHandle<String> retained = jobs.startOrJoin("key", false, value -> true, () -> "rival");
            assertEquals(first.snapshot().operationId(), retained.snapshot().operationId());
            OperationHandle<String> retry = jobs.startOrJoin("key", true, value -> true, () -> "retry");
            assertFalse(first.snapshot().operationId().equals(retry.snapshot().operationId()));
            assertFalse(first.cancel(), "an old attempt cannot cancel its replacement");
            await().atMost(5, SECONDS).until(() -> retry.snapshot().state().terminal());
            assertEquals(OperationState.COMPLETED, retry.snapshot().state());
        } finally {
            release.countDown();
        }
    }

    @Test
    void cancellationBeforeHookRegistrationStillCancelsTheUnderlyingTransport() throws Exception {
        BoundedJobs<String, String> jobs = new BoundedJobs<>(IMMEDIATE);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch installHook = new CountDownLatch(1);
        AtomicInteger cancellations = new AtomicInteger();
        OperationHandle<String> operation = jobs.startOrJoin("transport", false, value -> true, control -> {
            entered.countDown();
            while (installHook.getCount() != 0) {
                try {
                    installHook.await();
                } catch (InterruptedException ignored) {
                    // Let registration race a cancellation already requested by the client.
                }
            }
            control.onCancellation(cancellations::incrementAndGet);
            control.checkCancellation();
            return "result";
        });
        try {
            assertTrue(entered.await(5, SECONDS));
            assertTrue(operation.cancel());
            assertFalse(operation.cancel());
        } finally {
            installHook.countDown();
        }
        await().atMost(5, SECONDS).until(() -> operation.snapshot().state().terminal());
        assertEquals(1, cancellations.get());
        assertEquals(OperationState.CANCELLED, operation.snapshot().state());
    }

    @Test
    void cancellationDoesNotDiscardASuccessfullyPersistedResult() throws Exception {
        BoundedJobs<String, String> jobs = new BoundedJobs<>(IMMEDIATE);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        OperationHandle<String> operation = jobs.startOrJoin("persisted", false, value -> true, () -> {
            entered.countDown();
            awaitIgnoringInterrupts(release);
            return "persisted-recording";
        });
        try {
            assertTrue(entered.await(5, SECONDS));
            assertTrue(operation.cancel());
            assertEquals(OperationState.CANCEL_REQUESTED, operation.snapshot().state());
        } finally {
            release.countDown();
        }
        await().atMost(5, SECONDS).until(() -> operation.snapshot().state().terminal());
        assertEquals(OperationState.COMPLETED, operation.snapshot().state());
        assertTrue(operation.snapshot().cancellationRequested());
        assertEquals("persisted-recording", operation.snapshot().result());
    }

    @Test
    void cancellationDoesNotHideAnIndependentWorkerFailure() throws Exception {
        BoundedJobs<String, String> jobs = new BoundedJobs<>(IMMEDIATE);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        OperationHandle<String> operation = jobs.startOrJoin("disk", false, value -> true, () -> {
            entered.countDown();
            awaitIgnoringInterrupts(release);
            throw new IllegalStateException("disk full");
        });
        try {
            assertTrue(entered.await(5, SECONDS));
            assertTrue(operation.cancel());
        } finally {
            release.countDown();
        }
        await().atMost(5, SECONDS).until(() -> operation.snapshot().state().terminal());
        assertEquals(OperationState.FAILED, operation.snapshot().state());
        assertEquals("disk full", operation.snapshot().failure().getMessage());
        assertTrue(operation.snapshot().cancellationRequested());
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        while (latch.getCount() != 0) {
            try {
                latch.await();
            } catch (InterruptedException ignored) {
                // Deliberately uncooperative worker for cancellation outcome tests.
            }
        }
    }

    /**
     * A retained outcome outlives the call that produced it so a client can still read it, and then
     * stops outliving anything: the key is a recording id or a session reference, and one entry per
     * import for the life of the process is a map that only grows.
     */
    @Nested
    class CompletedSuccess {

        private final BoundedJobs<String, String> jobs = new BoundedJobs<>(GENEROUS);

        private Object runWithReuse(Predicate<String> reuse, Supplier<String> work) {
            var method = assertDoesNotThrow(() -> BoundedJobs.class.getMethod("runWithin",
                    Object.class, Duration.class, boolean.class, Predicate.class, Supplier.class));
            return assertDoesNotThrow(() -> method.invoke(jobs, "download", GENEROUS, false, reuse, work));
        }

        @Test
        void atomicallyReusesAPersistedSuccessWhenPreflightFinishesAfterTheTransfer() {
            jobs.runWithin("download", () -> "stored-recording");
            assertEquals(Optional.of("stored-recording"), runWithReuse("stored-recording"::equals, () -> {
                throw new AssertionError("A persisted completed transfer must not start again");
            }));
        }

        @Test
        void restartsWhenThePreviouslyDownloadedRecordingWasDeleted() {
            jobs.runWithin("download", () -> "deleted-recording");
            assertEquals(Optional.of("replacement"), runWithReuse(_ -> false, () -> "replacement"));
        }

        @Test
        void existingCallersStillStartFreshWorkAfterSuccess() {
            jobs.runWithin("download", () -> "first");
            assertEquals(Optional.of("second"), jobs.runWithin("download", () -> "second"));
        }
    }

    @Nested
    class Retention {

        private static final Duration RETENTION = Duration.ofMinutes(30);

        private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        private final BoundedJobs<String, String> jobs =
                new BoundedJobs<>(GENEROUS, RETENTION, clock);

        @Test
        void refusesARetentionOrClockThatCannotDateAnOutcome() {
            assertThrows(IllegalArgumentException.class,
                    () -> new BoundedJobs<>(GENEROUS, Duration.ZERO, clock));
            assertThrows(IllegalArgumentException.class,
                    () -> new BoundedJobs<>(GENEROUS, null, clock));
            assertThrows(IllegalArgumentException.class,
                    () -> new BoundedJobs<>(GENEROUS, RETENTION, null));
        }

        @Test
        void keepsAnOutcomeReadableWithinTheRetentionWindow() {
            jobs.runWithin("import-1", () -> "done");

            clock.advance(RETENTION.minusMinutes(1));

            assertTrue(jobs.outcome("import-1").isPresent());
        }

        @Test
        void sweepsOutcomesNothingIsGoingToRead() {
            jobs.runWithin("import-1", () -> "done");
            assertThrows(RuntimeException.class,
                    () -> jobs.runWithin("import-2", () -> {
                        throw new IllegalStateException("no such file");
                    }));

            clock.advance(RETENTION.plusMinutes(1));
            // The sweep runs where keys are added, so asking for unrelated work is what collects them.
            jobs.runWithin("import-3", () -> "done");

            assertTrue(jobs.outcome("import-1").isEmpty(), "a stale success must not be retained");
            assertTrue(jobs.outcome("import-2").isEmpty(), "a stale failure must not be retained");
            assertTrue(jobs.outcome("import-3").isPresent(), "the fresh outcome stays");
        }

        @Test
        void doesNotReportASweptFailureToTheCallerThatFollowsIt() {
            assertThrows(RuntimeException.class,
                    () -> jobs.runWithin("import-1", () -> {
                        throw new IllegalStateException("no such file");
                    }));

            clock.advance(RETENTION.plusMinutes(1));

            // retryFailure=false would rethrow a retained failure; a swept one starts fresh instead.
            assertEquals(Optional.of("done"),
                    jobs.runWithin("import-1", GENEROUS, false, () -> "done"));
        }
    }

    /**
     * An attempt that is never scheduled never runs its own finally, so nothing sets finishedAt --
     * and without it the sweep skips the entry, isRunning keeps saying yes, and every later call for
     * that key joins work that does not exist. The key has to stay askable.
     */
    @Nested
    class SchedulingFailure {

        private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

        @Test
        void aJobThatCannotBeScheduledLeavesItsKeyRetryable() {
            AtomicInteger scheduled = new AtomicInteger();
            BoundedJobs<String, String> jobs = new BoundedJobs<>(GENEROUS, Duration.ofMinutes(30), clock,
                    command -> {
                        if (scheduled.incrementAndGet() == 1) {
                            throw new RejectedExecutionException("shutting down");
                        }
                        command.run();
                    });

            assertThrows(RejectedExecutionException.class,
                    () -> jobs.runWithin("import-1", () -> "done"));

            assertFalse(jobs.isRunning("import-1"), "an attempt nothing will run is not running");
            assertTrue(jobs.outcome("import-1").isPresent(), "it must be readable as a failure");
            assertEquals(Optional.of("done"), jobs.runWithin("import-1", () -> "done"),
                    "the key must accept work again");
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration amount) {
            now = now.plus(amount);
        }

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

    @Nested
    class Construction {

        @Test
        void refusesABudgetThatIsNotPositive() {
            assertThrows(IllegalArgumentException.class, () -> new BoundedJobs<>(Duration.ZERO));
            assertThrows(IllegalArgumentException.class,
                    () -> new BoundedJobs<>(Duration.ofSeconds(-1)));
            assertThrows(IllegalArgumentException.class, () -> new BoundedJobs<>(null));
        }

        @Test
        void refusesAPerCallBudgetThatIsNotPositive() {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(GENEROUS);

            assertThrows(IllegalArgumentException.class,
                    () -> jobs.runWithin("r-1", Duration.ZERO, () -> "done"));
            assertThrows(IllegalArgumentException.class,
                    () -> jobs.runWithin("r-1", Duration.ofMillis(-1), () -> "done"));
            assertThrows(IllegalArgumentException.class,
                    () -> jobs.runWithin("r-1", null, () -> "done"));
        }
    }

    @Nested
    class InsideTheBudget {

        @Test
        void handsBackTheResultOfWorkThatFinishedInTime() {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(GENEROUS);

            assertEquals(Optional.of("done"), jobs.runWithin("r-1", () -> "done"));
        }

        /**
         * The failure the caller sees is the one the work threw. Reporting the ExecutionException
         * wrapper instead would give every failure the same message and hide the one saying which
         * file could not be parsed.
         */
        @Test
        void reportsTheFailureTheWorkActuallyThrew() {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(GENEROUS);

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> jobs.runWithin("r-1", () -> {
                        throw new IllegalArgumentException("recording is not a JFR file");
                    }));

            assertEquals("recording is not a JFR file", thrown.getMessage());
        }

        @Test
        void canRetryImmediatelyAfterTheFirstAttemptThrows() {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(GENEROUS);
            AtomicInteger attempts = new AtomicInteger();

            assertThrows(IllegalStateException.class, () -> jobs.runWithin("r-1", () -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("first attempt failed");
            }));

            assertEquals(Optional.of("retry succeeded"), jobs.runWithin("r-1", () -> {
                attempts.incrementAndGet();
                return "retry succeeded";
            }));
            assertEquals(2, attempts.get());
        }

        @Test
        void canInspectARetainedFailureWithoutStartingTheSupplierAgain() {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(GENEROUS);
            AtomicInteger attempts = new AtomicInteger();
            assertThrows(IllegalStateException.class, () -> jobs.runWithin("r-1", () -> {
                throw new IllegalStateException("first attempt failed");
            }));

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> jobs.runWithin("r-1", GENEROUS, false, () -> {
                        attempts.incrementAndGet();
                        return "must not run";
                    }));

            assertEquals("first attempt failed", failure.getMessage());
            assertEquals(0, attempts.get());
        }

        /**
         * A failed job must not be remembered as in flight, or the tool would report a transfer that
         * is running when nothing is.
         */
        @Test
        void forgetsAJobThatFailed() {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(GENEROUS);

            assertThrows(IllegalStateException.class, () -> jobs.runWithin("r-1", () -> {
                throw new IllegalStateException("hub stopped answering");
            }));

            await().atMost(5, SECONDS).untilAsserted(() -> assertFalse(jobs.isRunning("r-1")));
        }

        @Test
        void forgetsAJobThatSucceeded() {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(GENEROUS);
            jobs.runWithin("r-1", () -> "done");

            await().atMost(5, SECONDS).untilAsserted(() -> assertFalse(jobs.isRunning("r-1")));
        }
    }

    @Nested
    class PastTheBudget {

        /**
         * Not a failure: the work carries on and the caller is told how to follow it, which is what
         * stops a client from retrying and starting the whole transfer again.
         */
        @Test
        void handsBackNothingWhileTheWorkContinues() throws InterruptedException {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(IMMEDIATE);
            CountDownLatch release = new CountDownLatch(1);

            Optional<String> answer = jobs.runWithin("r-1", () -> {
                awaitQuietly(release);
                return "done";
            });

            assertTrue(answer.isEmpty());
            assertTrue(jobs.isRunning("r-1"));
            release.countDown();
            await().atMost(5, SECONDS).untilAsserted(() -> assertFalse(jobs.isRunning("r-1")));
        }

        @Test
        void retainsALateFailureForRepeatedStatusPolls() {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(IMMEDIATE);
            CountDownLatch release = new CountDownLatch(1);

            assertTrue(jobs.runWithin("r-1", () -> {
                awaitQuietly(release);
                throw new IllegalStateException("recording parser stopped");
            }).isEmpty());

            release.countDown();
            await().atMost(5, SECONDS).untilAsserted(() -> {
                BoundedJobs.Outcome<String> first = jobs.outcome("r-1").orElseThrow();
                BoundedJobs.Outcome<String> second = jobs.outcome("r-1").orElseThrow();

                assertEquals("recording parser stopped", first.failure().getMessage());
                assertEquals("recording parser stopped", second.failure().getMessage());
            });
        }

        @Test
        void anExplicitRetryReplacesARetainedFailure() {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(IMMEDIATE);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger attempts = new AtomicInteger();

            assertTrue(jobs.runWithin("r-1", () -> {
                attempts.incrementAndGet();
                awaitQuietly(release);
                throw new IllegalStateException("first attempt failed");
            }).isEmpty());
            release.countDown();
            await().atMost(5, SECONDS).until(() -> jobs.outcome("r-1").isPresent());

            assertEquals(Optional.of("retry succeeded"), jobs.runWithin("r-1", () -> {
                attempts.incrementAndGet();
                return "retry succeeded";
            }));
            assertEquals(2, attempts.get());
            assertEquals("retry succeeded", jobs.outcome("r-1").orElseThrow().value());
        }

        /**
         * The whole point of the key. A second call for the same recording joins the first; without
         * this it would import the same file, or pull the same session, a second time.
         */
        @Test
        void joinsWorkAlreadyRunningForTheSameKey() throws InterruptedException {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(IMMEDIATE);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger started = new AtomicInteger();

            jobs.runWithin("r-1", () -> {
                started.incrementAndGet();
                awaitQuietly(release);
                return "done";
            });
            Optional<String> second = jobs.runWithin("r-1", () -> {
                started.incrementAndGet();
                return "a rival";
            });

            assertTrue(second.isEmpty());
            assertEquals(1, started.get(), "the second call must not have started its own work");
            release.countDown();
            await().atMost(5, SECONDS).untilAsserted(() -> assertFalse(jobs.isRunning("r-1")));
        }

        @Test
        void runsDifferentKeysIndependently() throws InterruptedException {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(IMMEDIATE);
            CountDownLatch release = new CountDownLatch(1);

            jobs.runWithin("r-1", () -> {
                awaitQuietly(release);
                return "slow";
            });

            assertEquals(Optional.of("quick"), jobs.runWithin("r-2", () -> "quick"));
            release.countDown();
        }

        @Test
        void usesThePerCallBudgetWhenOneIsProvided() {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(GENEROUS);
            CountDownLatch release = new CountDownLatch(1);

            Optional<String> answer = jobs.runWithin("r-1", IMMEDIATE, () -> {
                awaitQuietly(release);
                return "done";
            });

            assertTrue(answer.isEmpty());
            assertTrue(jobs.isRunning("r-1"));
            release.countDown();
        }
    }

    @Test
    void reportsNothingRunningForAKeyItHasNeverSeen() {
        assertFalse(new BoundedJobs<String, String>(GENEROUS).isRunning("never-asked-for"));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
