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

package cafe.jeffrey.hub.core.scheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.hub.core.scheduler.history.JobExecution;
import cafe.jeffrey.hub.core.scheduler.history.JobExecutionHistory;
import cafe.jeffrey.hub.core.scheduler.history.JobExecutionStatus;
import cafe.jeffrey.shared.common.model.job.JobType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class PeriodicalSchedulerTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private final JobExecutionHistory history = new JobExecutionHistory();

    private PeriodicalScheduler scheduler;

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.close();
        }
    }

    private PeriodicalScheduler newScheduler(List<? extends Job> jobs) {
        return new PeriodicalScheduler(jobs, history, FIXED_CLOCK);
    }

    private static Job testJob(Runnable action, Duration period) {
        return testJob(action, period, JobType.PROJECTS_SYNCHRONIZER);
    }

    private static Job testJob(Runnable action, Duration period, JobType jobType) {
        return reportingTestJob(_ -> action.run(), period, jobType);
    }

    private static Job reportingTestJob(Consumer<JobContext> action, Duration period, JobType jobType) {
        return new Job() {
            @Override
            public void execute(JobContext context) {
                action.accept(context);
            }

            @Override
            public Duration period() {
                return period;
            }

            @Override
            public JobType jobType() {
                return jobType;
            }
        };
    }

    @Nested
    class Start {

        @Test
        void schedulesAllJobs_atFixedRate() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(2);
            Job job = testJob(latch::countDown, Duration.ofMillis(50));

            scheduler = newScheduler(List.of(job));
            scheduler.start();

            assertTrue(latch.await(2, TimeUnit.SECONDS));
        }

        @Test
        void startIsIdempotent_calledTwice_onlyOneScheduler() {
            AtomicInteger counter = new AtomicInteger();
            Job job = testJob(counter::incrementAndGet, Duration.ofMillis(50));

            scheduler = newScheduler(List.of(job));
            scheduler.start();
            scheduler.start();

            // With one scheduler at 50ms intervals, the counter grows steadily; a duplicated
            // schedule would roughly double the rate. Wait for a few executions and check
            // the count stayed in the single-schedule range.
            await().atMost(2, SECONDS).until(() -> counter.get() >= 3);
            assertTrue(counter.get() < 10);
        }
    }

    @Nested
    class Submit {

        @Test
        void submitsJobImmediately_whenSchedulerStarted() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            Job periodicJob = testJob(() -> {}, Duration.ofSeconds(60));
            Job immediateJob = testJob(latch::countDown, Duration.ofSeconds(60));

            scheduler = newScheduler(List.of(periodicJob));
            scheduler.start();

            CompletableFuture<Void> future = scheduler.submit(immediateJob);
            assertNotNull(future);
            assertTrue(latch.await(2, TimeUnit.SECONDS));
        }

        @Test
        void returnsFailedFuture_whenSchedulerNotStarted() {
            Job job = testJob(() -> {}, Duration.ofSeconds(60));
            scheduler = newScheduler(List.of());

            CompletableFuture<Void> result = scheduler.submit(job);

            // Never null — callers chain orTimeout()/whenComplete() directly on the result
            assertNotNull(result);
            assertTrue(result.isCompletedExceptionally());
            ExecutionException e = assertThrows(ExecutionException.class, result::get);
            assertInstanceOf(IllegalStateException.class, e.getCause());
        }
    }

    @Nested
    class FailureResilience {

        @Test
        void jobThrowingError_staysScheduled() {
            AtomicInteger executions = new AtomicInteger();
            Job job = testJob(() -> {
                if (executions.incrementAndGet() == 1) {
                    // An Error (not an Exception) escaping the tick would silently cancel
                    // the job in scheduleAtFixedRate for the process lifetime
                    throw new AssertionError("boom on first tick");
                }
            }, Duration.ofMillis(50));

            scheduler = newScheduler(List.of(job));
            scheduler.start();

            await().atMost(2, SECONDS).until(() -> executions.get() >= 3);
        }

        @Test
        void sameJob_neverRunsConcurrently_periodicAndOnDemand() throws Exception {
            AtomicInteger concurrent = new AtomicInteger();
            AtomicInteger maxConcurrent = new AtomicInteger();
            Job job = testJob(() -> {
                int current = concurrent.incrementAndGet();
                maxConcurrent.accumulateAndGet(current, Math::max);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    concurrent.decrementAndGet();
                }
            }, Duration.ofMillis(20));

            scheduler = newScheduler(List.of(job));
            scheduler.start();

            // Fire on-demand executions while the periodic schedule is running
            CompletableFuture<Void> submitted = scheduler.submit(job);
            submitted.get(5, TimeUnit.SECONDS);

            await().atMost(2, SECONDS).until(() -> maxConcurrent.get() >= 1);
            assertEquals(1, maxConcurrent.get(), "The same job type must be serialized");
        }
    }

    @Nested
    class Close {

        @Test
        void shutsDownExecutor() {
            AtomicInteger counter = new AtomicInteger();
            Job job = testJob(counter::incrementAndGet, Duration.ofMillis(50));

            scheduler = newScheduler(List.of(job));
            scheduler.start();
            await().atMost(2, SECONDS).until(() -> counter.get() > 0);

            scheduler.close();

            // An in-flight tick may still finish right after close(); wait until the counter
            // settles, then require it to stay flat — no new executions after shutdown
            AtomicInteger lastSeen = new AtomicInteger(counter.get());
            await().atMost(2, SECONDS).until(() -> {
                int current = counter.get();
                return current == lastSeen.getAndSet(current);
            });
            int countAfterClose = counter.get();
            await().during(300, MILLISECONDS).atMost(2, SECONDS)
                    .until(() -> counter.get() == countAfterClose);
        }

        @Test
        void closeIsNoOp_whenNeverStarted() {
            scheduler = newScheduler(List.of());
            assertDoesNotThrow(() -> scheduler.close());
        }
    }

    @Nested
    class ExecutionHistoryRecording {

        @Test
        void successfulRun_recordsSummaryAndItems() throws Exception {
            Job job = reportingTestJob(context -> {
                context.report().summary("Purged 2 soft-deleted projects");
                context.report().item("Purged project: legacy-billing");
                context.report().item("Purged project: demo-sandbox");
            }, Duration.ofSeconds(60), JobType.DELETED_PROJECTS_CLEANER);

            scheduler = newScheduler(List.of());
            scheduler.start();
            scheduler.submit(job).get(5, TimeUnit.SECONDS);

            await().atMost(2, SECONDS).untilAsserted(() -> {
                List<JobExecution> executions = history.all();
                assertEquals(1, executions.size());
                JobExecution execution = executions.getFirst();
                assertEquals(JobType.DELETED_PROJECTS_CLEANER, execution.jobType());
                assertEquals(JobExecutionStatus.SUCCESS, execution.status());
                assertEquals(FIXED_CLOCK.instant(), execution.startedAt());
                assertEquals("Purged 2 soft-deleted projects", execution.summary());
                assertEquals(
                        List.of("Purged project: legacy-billing", "Purged project: demo-sandbox"),
                        execution.items());
                assertNull(execution.error());
                assertFalse(execution.noop());
            });
        }

        @Test
        void runReportingNothing_isRecordedAsNoop() throws Exception {
            Job job = testJob(() -> {}, Duration.ofSeconds(60), JobType.TEMP_DIRECTORY_CLEANER);

            scheduler = newScheduler(List.of());
            scheduler.start();
            scheduler.submit(job).get(5, TimeUnit.SECONDS);

            await().atMost(2, SECONDS).untilAsserted(() -> {
                List<JobExecution> executions = history.all();
                assertEquals(1, executions.size());
                JobExecution execution = executions.getFirst();
                assertEquals(JobExecutionStatus.SUCCESS, execution.status());
                assertTrue(execution.noop());
            });
        }

        @Test
        void throwingRun_isRecordedAsFailure_withError() {
            Job job = testJob(() -> {
                throw new IllegalStateException("boom");
            }, Duration.ofMillis(50), JobType.WORKSPACE_EVENTS_REPLICATOR);

            scheduler = newScheduler(List.of(job));
            scheduler.start();

            await().atMost(2, SECONDS).untilAsserted(() -> {
                List<JobExecution> executions = history.all();
                assertFalse(executions.isEmpty());
                JobExecution execution = executions.getFirst();
                assertEquals(JobExecutionStatus.FAILURE, execution.status());
                assertTrue(execution.error().contains("boom"));
                assertFalse(execution.noop());
            });
        }

        @Test
        void reportedPartialFailure_marksRunAsFailure() throws Exception {
            Job job = reportingTestJob(context -> {
                context.report().item("project=proj-001 cleaned");
                context.report().failure("project=proj-002 failed: storage unreachable");
            }, Duration.ofSeconds(60), JobType.EXPIRED_INSTANCE_CLEANER);

            scheduler = newScheduler(List.of());
            scheduler.start();
            scheduler.submit(job).get(5, TimeUnit.SECONDS);

            await().atMost(2, SECONDS).untilAsserted(() -> {
                List<JobExecution> executions = history.all();
                assertEquals(1, executions.size());
                JobExecution execution = executions.getFirst();
                assertEquals(JobExecutionStatus.FAILURE, execution.status());
                assertEquals("project=proj-002 failed: storage unreachable", execution.error());
                assertEquals(
                        List.of("project=proj-001 cleaned", "project=proj-002 failed: storage unreachable"),
                        execution.items());
            });
        }
    }
}
