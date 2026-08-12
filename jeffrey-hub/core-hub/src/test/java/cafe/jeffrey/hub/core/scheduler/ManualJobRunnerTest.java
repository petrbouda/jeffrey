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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.common.model.job.JobType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
        public void execute(JobContext context) {
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
                    new TriggerableJob(JobType.WORKSPACE_RECONCILER, "2 entities"),
                    new ScheduledOnlyJob(JobType.EXPIRED_INSTANCE_CLEANER)), FIXED_CLOCK);

            assertEquals(Set.of(JobType.WORKSPACE_RECONCILER), runner.supportedTypes());
        }

        @Test
        void reportsNothingWhenNoJobOptedIn() {
            var runner = new ManualJobRunner(
                    List.of(new ScheduledOnlyJob(JobType.EXPIRED_INSTANCE_CLEANER)), FIXED_CLOCK);

            assertTrue(runner.supportedTypes().isEmpty());
        }
    }

    @Nested
    class Running {

        @Test
        void runsTheJobAndReturnsItsOwnSummary() {
            var job = new TriggerableJob(JobType.WORKSPACE_RECONCILER, "3 entities from 2 workspaces");
            var runner = new ManualJobRunner(List.of(job), FIXED_CLOCK);

            ManualJobRunner.Result result = runner.run(JobType.WORKSPACE_RECONCILER);

            assertAll(
                    () -> assertEquals(1, job.runs.get()),
                    () -> assertEquals(JobType.WORKSPACE_RECONCILER, result.jobType()),
                    () -> assertEquals(NOW, result.startedAt()),
                    () -> assertEquals("3 entities from 2 workspaces", result.summary()),
                    () -> assertTrue(result.durationMs() >= 0));
        }

        @Test
        void routesToTheRequestedJobOnly() {
            var reconciler = new TriggerableJob(JobType.WORKSPACE_RECONCILER, "reconciled");
            var storage = new TriggerableJob(JobType.STORAGE_OVERVIEW_REFRESHER, "refreshed");
            var runner = new ManualJobRunner(List.of(reconciler, storage), FIXED_CLOCK);

            assertEquals("refreshed", runner.run(JobType.STORAGE_OVERVIEW_REFRESHER).summary());
            assertEquals(0, reconciler.runs.get());
        }

        @Test
        void rejectsAJobThatDoesNotOfferAManualRun() {
            var runner = new ManualJobRunner(
                    List.of(new ScheduledOnlyJob(JobType.EXPIRED_INSTANCE_CLEANER)), FIXED_CLOCK);

            var e = assertThrows(ManualJobRunner.ManualRunNotSupportedException.class,
                    () -> runner.run(JobType.EXPIRED_INSTANCE_CLEANER));
            assertTrue(e.getMessage().contains("EXPIRED_INSTANCE_CLEANER"),
                    "The error should name the job that was asked for");
        }

        @Test
        void rejectsAJobTypeThatHasNoRegisteredJob() {
            var runner = new ManualJobRunner(List.of(), FIXED_CLOCK);

            assertThrows(ManualJobRunner.ManualRunNotSupportedException.class,
                    () -> runner.run(JobType.WORKSPACE_RECONCILER));
        }

        @Test
        void aFailingJobPropagatesRatherThanReportingSuccess() {
            var failing = new TriggerableJob(JobType.WORKSPACE_RECONCILER, "unused") {
                @Override
                public String runManually() {
                    throw new IllegalStateException("volume unavailable");
                }
            };
            var runner = new ManualJobRunner(List.of(failing), FIXED_CLOCK);

            assertThrows(IllegalStateException.class, () -> runner.run(JobType.WORKSPACE_RECONCILER));
        }
    }
}
