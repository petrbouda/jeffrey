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

package cafe.jeffrey.volumescheduler.scheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private JobScheduler scheduler;

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.close();
        }
    }

    private static Job job(JobType type, Runnable action) {
        return new Job() {
            @Override
            public JobType type() {
                return type;
            }

            @Override
            public String run() {
                action.run();
                return "ran " + type.id();
            }
        };
    }

    private static ScheduledJob every(Duration period, Job job) {
        return new ScheduledJob(job, new JobSchedule(true, period));
    }

    private static ScheduledJob disabled(Job job) {
        return new ScheduledJob(job, new JobSchedule(false, Duration.ofMillis(10)));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Counts how many runs overlap: a job that must never run beside itself keeps it at one. */
    private static final class Overlap implements Runnable {

        private final AtomicInteger concurrent = new AtomicInteger();
        private final AtomicInteger max = new AtomicInteger();
        private final AtomicInteger runs = new AtomicInteger();
        private final long millis;

        private Overlap(long millis) {
            this.millis = millis;
        }

        @Override
        public void run() {
            max.accumulateAndGet(concurrent.incrementAndGet(), Math::max);
            sleep(millis);
            concurrent.decrementAndGet();
            runs.incrementAndGet();
        }
    }

    @Nested
    class Start {

        @Test
        void runsAnEnabledJobPeriodically() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(2);
            scheduler = new JobScheduler(List.of(every(Duration.ofMillis(50), job(JobType.RETENTION, latch::countDown))), CLOCK);

            scheduler.start();

            assertTrue(latch.await(2, SECONDS));
        }

        @Test
        void neverRunsADisabledJobOnItsOwn() {
            AtomicInteger runs = new AtomicInteger();
            CountDownLatch enabledRan = new CountDownLatch(3);
            scheduler = new JobScheduler(List.of(
                    disabled(job(JobType.RETENTION, runs::incrementAndGet)),
                    every(Duration.ofMillis(10), job(JobType.COMPRESSION, enabledRan::countDown))), CLOCK);

            scheduler.start();

            await().atMost(2, SECONDS).until(() -> enabledRan.getCount() == 0);
            assertEquals(0, runs.get());
        }

        @Test
        void fixedDelayNeverProducesCatchUpBursts() {
            // With fixed-rate semantics a 100ms job on a 100ms period starts back-to-back;
            // with fixed-delay each start is >= sleep + period after the previous one.
            List<Long> startTimes = new CopyOnWriteArrayList<>();
            scheduler = new JobScheduler(List.of(every(Duration.ofMillis(100), job(JobType.RETENTION, () -> {
                startTimes.add(System.nanoTime());
                sleep(100);
            }))), CLOCK);

            scheduler.start();

            await().atMost(5, SECONDS).until(() -> startTimes.size() >= 3);
            for (int i = 1; i < 3; i++) {
                long gapMillis = TimeUnit.NANOSECONDS.toMillis(startTimes.get(i) - startTimes.get(i - 1));
                assertTrue(gapMillis >= 180,
                        "Fixed-delay start-to-start gap must be at least sleep + period, was " + gapMillis + "ms");
            }
        }

        @Test
        void startingTwiceSchedulesOnce() {
            AtomicInteger counter = new AtomicInteger();
            scheduler = new JobScheduler(List.of(every(Duration.ofMillis(50), job(JobType.RETENTION, counter::incrementAndGet))), CLOCK);

            scheduler.start();
            scheduler.start();

            // At 50ms intervals the counter grows steadily; a duplicated schedule would roughly
            // double the rate
            await().atMost(2, SECONDS).until(() -> counter.get() >= 3);
            assertTrue(counter.get() < 10);
        }

        @Test
        void twoJobsOfOneTypeAreRefused() {
            assertThrows(IllegalArgumentException.class, () -> new JobScheduler(List.of(
                    every(Duration.ofMinutes(1), job(JobType.RETENTION, () -> { })),
                    every(Duration.ofMinutes(1), job(JobType.RETENTION, () -> { }))), CLOCK));
        }
    }

    @Nested
    class FailureResilience {

        @Test
        void aJobThrowingAnErrorStaysScheduled() {
            AtomicInteger executions = new AtomicInteger();
            scheduler = new JobScheduler(List.of(every(Duration.ofMillis(50), job(JobType.RETENTION, () -> {
                if (executions.incrementAndGet() == 1) {
                    // An Error (not an Exception) escaping the run would silently cancel the job in
                    // scheduleWithFixedDelay for the process lifetime
                    throw new AssertionError("boom on first run");
                }
            }))), CLOCK);

            scheduler.start();

            await().atMost(2, SECONDS).until(() -> executions.get() >= 3);
        }

        @Test
        void aRunOnDemandThatFailsPropagates() {
            scheduler = new JobScheduler(List.of(every(Duration.ofMinutes(1), job(JobType.RETENTION, () -> {
                throw new IllegalStateException("volume went away");
            }))), CLOCK);

            assertThrows(IllegalStateException.class, () -> scheduler.runNow(JobType.RETENTION));
        }
    }

    @Nested
    class Locks {

        @Test
        void theSameJobNeverRunsBesideItselfWhenARunOnDemandMeetsAScheduledOne() throws Exception {
            Overlap overlap = new Overlap(50);
            scheduler = new JobScheduler(List.of(every(Duration.ofMillis(20), job(JobType.RETENTION, overlap))), CLOCK);
            scheduler.start();

            CompletableFuture.runAsync(() -> scheduler.runNow(JobType.RETENTION)).get(5, SECONDS);

            await().atMost(2, SECONDS).until(() -> overlap.runs.get() >= 3);
            assertEquals(1, overlap.max.get(), "the same job must be serialized");
        }

        @Test
        void differentJobsRunSideBySide() {
            Overlap overlap = new Overlap(200);
            scheduler = new JobScheduler(List.of(
                    every(Duration.ofMillis(20), job(JobType.RETENTION, overlap)),
                    every(Duration.ofMillis(20), job(JobType.COMPRESSION, overlap))), CLOCK);

            scheduler.start();

            await().atMost(2, SECONDS).until(() -> overlap.max.get() >= 2);
        }
    }

    @Nested
    class RunNow {

        @Test
        void runsTheJobAndReturnsItsOwnSummary() {
            scheduler = new JobScheduler(List.of(every(Duration.ofMinutes(1), job(JobType.RETENTION, () -> { }))), CLOCK);

            JobRun run = scheduler.runNow(JobType.RETENTION);

            assertEquals(JobType.RETENTION, run.type());
            assertEquals(NOW, run.startedAt());
            assertEquals("ran retention", run.summary());
        }

        @Test
        void aDisabledJobStillRunsOnDemand() {
            AtomicBoolean ran = new AtomicBoolean();
            scheduler = new JobScheduler(List.of(disabled(job(JobType.COMPRESSION, () -> ran.set(true)))), CLOCK);

            scheduler.runNow(JobType.COMPRESSION);

            assertTrue(ran.get());
        }

        @Test
        void aJobTypeWithNoJobIsRefused() {
            scheduler = new JobScheduler(List.of(every(Duration.ofMinutes(1), job(JobType.RETENTION, () -> { }))), CLOCK);

            assertThrows(IllegalArgumentException.class, () -> scheduler.runNow(JobType.COMPRESSION));
        }
    }

    @Nested
    class Status {

        @Test
        void listsEveryJobInTheOrderTheTypesDeclareThem() {
            JobSchedule compression = new JobSchedule(true, Duration.ofMinutes(1));
            JobSchedule retention = new JobSchedule(false, Duration.ofMinutes(5));
            scheduler = new JobScheduler(List.of(
                    new ScheduledJob(job(JobType.COMPRESSION, () -> { }), compression),
                    new ScheduledJob(job(JobType.RETENTION, () -> { }), retention)), CLOCK);

            assertEquals(List.of(
                            new JobStatus(JobType.RETENTION, retention),
                            new JobStatus(JobType.COMPRESSION, compression)),
                    scheduler.jobs());
            assertEquals(Optional.of(new JobStatus(JobType.COMPRESSION, compression)), scheduler.job(JobType.COMPRESSION));
        }
    }

    @Nested
    class Close {

        @Test
        void noRunStartsAfterClose() {
            AtomicInteger counter = new AtomicInteger();
            scheduler = new JobScheduler(List.of(every(Duration.ofMillis(50), job(JobType.RETENTION, counter::incrementAndGet))), CLOCK);
            scheduler.start();
            await().atMost(2, SECONDS).until(() -> counter.get() > 0);

            scheduler.close();

            int countAfterClose = counter.get();
            await().during(300, MILLISECONDS).atMost(2, SECONDS).until(() -> counter.get() == countAfterClose);
        }

        /**
         * A deletion or a compression rename cut off half-way is the state every job is written
         * never to leave behind, so closing waits for the run in flight rather than interrupting it.
         */
        @Test
        void waitsForTheRunInFlightWithoutInterruptingIt() throws InterruptedException {
            CountDownLatch started = new CountDownLatch(1);
            AtomicBoolean interrupted = new AtomicBoolean();
            AtomicBoolean finished = new AtomicBoolean();
            scheduler = new JobScheduler(List.of(every(Duration.ofMinutes(1), job(JobType.RETENTION, () -> {
                started.countDown();
                try {
                    Thread.sleep(300);
                    finished.set(true);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                }
            }))), CLOCK);
            scheduler.start();
            assertTrue(started.await(2, SECONDS));

            scheduler.close();

            assertTrue(finished.get(), "close returned only once the run had finished");
            assertFalse(interrupted.get());
        }

        @Test
        void closingANeverStartedSchedulerDoesNothing() {
            scheduler = new JobScheduler(List.of(), CLOCK);
            assertDoesNotThrow(() -> scheduler.close());
        }
    }
}
