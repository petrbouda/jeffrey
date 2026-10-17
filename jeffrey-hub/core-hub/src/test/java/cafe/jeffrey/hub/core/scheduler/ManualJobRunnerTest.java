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

package cafe.jeffrey.hub.core.scheduler;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.hub.model.job.JobType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A job opts into manual running by implementing {@link ManuallyTriggerable} and nothing else —
 * the runner discovers it from the registered jobs, so no list anywhere names a job type.
 */
class ManualJobRunnerTest {

    private static final Instant NOW = Instant.parse("2026-02-20T15:30:45Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /** A scheduled-only job: no manual run offered. */
    private static class ScheduledOnlyJob implements Job {

        private final JobType jobType;

        ScheduledOnlyJob(JobType jobType) {
            this.jobType = jobType;
        }

        @Override
        public void execute() {
        }

        @Override
        public Duration period() {
            return Duration.ofMinutes(1);
        }

        @Override
        public JobType jobType() {
            return jobType;
        }
    }

    private static class TriggerableJob extends ScheduledOnlyJob implements ManuallyTriggerable {

        private final AtomicInteger runs = new AtomicInteger();
        private final String summary;

        TriggerableJob(JobType jobType, String summary) {
            super(jobType);
            this.summary = summary;
        }

        @Override
        public String runManually() {
            runs.incrementAndGet();
            return summary;
        }
    }

    @Nested
    class SupportedTypes {

        @Test
        void reportsOnlyJobsThatImplementTheInterface() {
            var runner = new ManualJobRunner(List.of(
                    new TriggerableJob(JobType.PROJECT_RETENTION, "2 entities"),
                    new ScheduledOnlyJob(JobType.REPOSITORY_JFR_COMPRESSION)), new JobLocks(), FIXED_CLOCK);

            assertEquals(Set.of(JobType.PROJECT_RETENTION), runner.supportedTypes());
        }

        @Test
        void reportsNothingWhenNoJobOptedIn() {
            var runner = new ManualJobRunner(List.of(new ScheduledOnlyJob(JobType.REPOSITORY_JFR_COMPRESSION)), new JobLocks(), FIXED_CLOCK);

            assertTrue(runner.supportedTypes().isEmpty());
        }
    }

    @Nested
    class Running {

        @Test
        void runsTheJobAndReturnsItsOwnSummary() {
            var job = new TriggerableJob(JobType.PROJECT_RETENTION, "3 entities from 2 workspaces");
            var runner = new ManualJobRunner(List.of(job), new JobLocks(), FIXED_CLOCK);

            ManualJobRunner.Result result = runner.run(JobType.PROJECT_RETENTION);

            assertAll(
                    () -> assertEquals(1, job.runs.get()),
                    () -> assertEquals(JobType.PROJECT_RETENTION, result.jobType()),
                    () -> assertEquals(NOW, result.startedAt()),
                    () -> assertEquals("3 entities from 2 workspaces", result.summary()),
                    () -> assertTrue(result.durationMs() >= 0));
        }

        @Test
        void routesToTheRequestedJobOnly() {
            var compression = new TriggerableJob(JobType.REPOSITORY_JFR_COMPRESSION, "compressed");
            var retention = new TriggerableJob(JobType.PROJECT_RETENTION, "retained");
            var runner = new ManualJobRunner(List.of(compression, retention), new JobLocks(), FIXED_CLOCK);

            assertEquals("retained", runner.run(JobType.PROJECT_RETENTION).summary());
            assertEquals(0, compression.runs.get());
        }

        @Test
        void rejectsAJobThatDoesNotOfferAManualRun() {
            var runner = new ManualJobRunner(List.of(new ScheduledOnlyJob(JobType.REPOSITORY_JFR_COMPRESSION)), new JobLocks(), FIXED_CLOCK);

            var e = assertThrows(ManualJobRunner.ManualRunNotSupportedException.class,
                    () -> runner.run(JobType.REPOSITORY_JFR_COMPRESSION));
            assertTrue(e.getMessage().contains("REPOSITORY_JFR_COMPRESSION"),
                    "The error should name the job that was asked for");
        }

        @Test
        void rejectsAJobTypeThatHasNoRegisteredJob() {
            var runner = new ManualJobRunner(List.of(), new JobLocks(), FIXED_CLOCK);

            assertThrows(ManualJobRunner.ManualRunNotSupportedException.class,
                    () -> runner.run(JobType.PROJECT_RETENTION));
        }

        @Test
        void aFailingJobPropagatesRatherThanReportingSuccess() {
            var failing = new TriggerableJob(JobType.PROJECT_RETENTION, "unused") {
                @Override
                public String runManually() {
                    throw new IllegalStateException("volume unavailable");
                }
            };
            var runner = new ManualJobRunner(List.of(failing), new JobLocks(), FIXED_CLOCK);

            assertThrows(IllegalStateException.class, () -> runner.run(JobType.PROJECT_RETENTION));
        }

        /**
         * A manual run and the scheduler's tick take the same lock, so an operator's run waits
         * for a tick in flight instead of walking the same tree beside it.
         */
        @Test
        void waitsForTheJobsScheduledTickToFinish() throws Exception {
            var job = new TriggerableJob(JobType.PROJECT_RETENTION, "done");
            var locks = new JobLocks();
            var runner = new ManualJobRunner(List.of(job), locks, FIXED_CLOCK);
            var tickRunning = new CountDownLatch(1);
            var releaseTick = new CountDownLatch(1);
            Thread tick = new Thread(() -> locks.exclusively(job, () -> {
                tickRunning.countDown();
                try {
                    releaseTick.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            tick.start();
            assertTrue(tickRunning.await(5, TimeUnit.SECONDS));

            var manual = CompletableFuture.supplyAsync(() -> runner.run(JobType.PROJECT_RETENTION));

            assertFalse(manual.isDone(), "the manual run must wait for the tick");
            releaseTick.countDown();
            assertEquals("done", manual.get(5, TimeUnit.SECONDS).summary());
            tick.join();
        }
    }
}
