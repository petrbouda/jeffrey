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
import cafe.jeffrey.shared.common.model.job.JobType;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class PeriodicalSchedulerTest {

    private PeriodicalScheduler scheduler;

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.close();
        }
    }

    private static Job testJob(Runnable action, Duration period) {
        return testJob(action, period, JobType.PROJECTS_SYNCHRONIZER, Job.ExecutorGroup.GLOBAL);
    }

    private static Job fanOutTestJob(Runnable action, Duration period) {
        return testJob(action, period, JobType.SESSION_FINISHED_DETECTOR, Job.ExecutorGroup.PROJECT_FAN_OUT);
    }

    private static Job testJob(Runnable action, Duration period, JobType jobType, Job.ExecutorGroup executorGroup) {
        return new Job() {
            @Override
            public void execute(JobContext context) {
                action.run();
            }

            @Override
            public Duration period() {
                return period;
            }

            @Override
            public JobType jobType() {
                return jobType;
            }

            @Override
            public ExecutorGroup executorGroup() {
                return executorGroup;
            }
        };
    }

    @Nested
    class Start {

        @Test
        void schedulesAllJobs_periodically() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(2);
            Job job = testJob(latch::countDown, Duration.ofMillis(50));

            scheduler = new PeriodicalScheduler(List.of(job));
            scheduler.start();

            assertTrue(latch.await(2, TimeUnit.SECONDS));
        }

        @Test
        void fixedDelay_slowJob_neverProducesCatchUpBursts() {
            // With fixed-rate semantics a 100ms job on a 100ms period starts back-to-back;
            // with fixed-delay each start is >= sleep + period after the previous one.
            List<Long> startTimes = new CopyOnWriteArrayList<>();
            Job job = testJob(() -> {
                startTimes.add(System.nanoTime());
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, Duration.ofMillis(100));

            scheduler = new PeriodicalScheduler(List.of(job));
            scheduler.start();

            await().atMost(5, SECONDS).until(() -> startTimes.size() >= 3);

            for (int i = 1; i < 3; i++) {
                long gapMillis = TimeUnit.NANOSECONDS.toMillis(startTimes.get(i) - startTimes.get(i - 1));
                assertTrue(gapMillis >= 180,
                        "Fixed-delay start-to-start gap must be at least sleep + period, was " + gapMillis + "ms");
            }
        }

        @Test
        void startIsIdempotent_calledTwice_onlyOneScheduler() {
            AtomicInteger counter = new AtomicInteger();
            Job job = testJob(counter::incrementAndGet, Duration.ofMillis(50));

            scheduler = new PeriodicalScheduler(List.of(job));
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

            scheduler = new PeriodicalScheduler(List.of(periodicJob));
            scheduler.start();

            CompletableFuture<Void> future = scheduler.submit(immediateJob);
            assertNotNull(future);
            assertTrue(latch.await(2, TimeUnit.SECONDS));
        }

        @Test
        void returnsFailedFuture_whenSchedulerNotStarted() {
            Job job = testJob(() -> {}, Duration.ofSeconds(60));
            scheduler = new PeriodicalScheduler(List.of());

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

            scheduler = new PeriodicalScheduler(List.of(job));
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

            scheduler = new PeriodicalScheduler(List.of(job));
            scheduler.start();

            // Fire on-demand executions while the periodic schedule is running
            CompletableFuture<Void> submitted = scheduler.submit(job);
            submitted.get(5, TimeUnit.SECONDS);

            await().atMost(2, SECONDS).until(() -> maxConcurrent.get() >= 1);
            assertEquals(1, maxConcurrent.get(), "The same job type must be serialized");
        }
    }

    @Nested
    class ExecutorGroups {

        @Test
        void slowFanOutJob_doesNotDelayGlobalJob() {
            AtomicInteger globalTicks = new AtomicInteger();
            Job slowFanOut = fanOutTestJob(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, Duration.ofMillis(10));
            Job fastGlobal = testJob(globalTicks::incrementAndGet, Duration.ofMillis(50));

            scheduler = new PeriodicalScheduler(List.of(slowFanOut, fastGlobal), 1);
            scheduler.start();

            // On a shared single thread the 500ms fan-out job would starve the global job
            await().atMost(2, SECONDS).until(() -> globalTicks.get() >= 5);
        }

        @Test
        void differentFanOutJobs_runConcurrently_withPoolSizeTwo() {
            AtomicInteger concurrent = new AtomicInteger();
            AtomicInteger maxConcurrent = new AtomicInteger();
            Runnable overlapping = () -> {
                int current = concurrent.incrementAndGet();
                maxConcurrent.accumulateAndGet(current, Math::max);
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    concurrent.decrementAndGet();
                }
            };
            Job first = fanOutTestJob(overlapping, Duration.ofMillis(20));
            Job second = fanOutTestJob(overlapping, Duration.ofMillis(20));

            scheduler = new PeriodicalScheduler(List.of(first, second), 2);
            scheduler.start();

            await().atMost(2, SECONDS).until(() -> maxConcurrent.get() >= 2);
        }

        @Test
        void sameFanOutJob_neverRunsConcurrently_evenWithLargerPool() throws Exception {
            AtomicInteger concurrent = new AtomicInteger();
            AtomicInteger maxConcurrent = new AtomicInteger();
            AtomicInteger executions = new AtomicInteger();
            Job job = fanOutTestJob(() -> {
                int current = concurrent.incrementAndGet();
                maxConcurrent.accumulateAndGet(current, Math::max);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    executions.incrementAndGet();
                    concurrent.decrementAndGet();
                }
            }, Duration.ofMillis(20));

            scheduler = new PeriodicalScheduler(List.of(job), 4);
            scheduler.start();

            // Race an on-demand execution against the periodic schedule on the pool
            CompletableFuture<Void> submitted = scheduler.submit(job);
            submitted.get(5, TimeUnit.SECONDS);

            await().atMost(2, SECONDS).until(() -> executions.get() >= 3);
            assertEquals(1, maxConcurrent.get(), "The same job must be serialized even on a pool");
        }
    }

    @Nested
    class Close {

        @Test
        void shutsDownExecutor() {
            AtomicInteger counter = new AtomicInteger();
            Job job = testJob(counter::incrementAndGet, Duration.ofMillis(50));

            scheduler = new PeriodicalScheduler(List.of(job));
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
            scheduler = new PeriodicalScheduler(List.of());
            assertDoesNotThrow(() -> scheduler.close());
        }
    }
}
