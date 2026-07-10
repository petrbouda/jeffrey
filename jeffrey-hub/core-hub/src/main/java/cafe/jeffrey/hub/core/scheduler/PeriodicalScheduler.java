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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.scheduler.history.CollectingJobExecutionReport;
import cafe.jeffrey.hub.core.scheduler.history.JobExecution;
import cafe.jeffrey.hub.core.scheduler.history.JobExecutionHistory;
import cafe.jeffrey.hub.core.scheduler.history.JobExecutionStatus;
import cafe.jeffrey.shared.common.CompletableFutures;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.measure.Measuring;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

public class PeriodicalScheduler implements Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(PeriodicalScheduler.class);

    private static final Duration DEFAULT_POLLING_DURATION = Duration.ofMillis(10);

    private final List<? extends Job> jobs;
    private final JobExecutionHistory executionHistory;
    private final Clock clock;

    private ScheduledExecutorService scheduler;

    public PeriodicalScheduler(List<? extends Job> jobs, JobExecutionHistory executionHistory, Clock clock) {
        this.jobs = jobs;
        this.executionHistory = executionHistory;
        this.clock = clock;
    }

    @Override
    public void start() {
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(
                    Schedulers.platformThreadfactory("periodical-scheduler"));

            for (Job job : jobs) {
                scheduler.scheduleAtFixedRate(
                        new ExecutedJob(job, JobContext.EMPTY, executionHistory, clock),
                        0, job.period().toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public CompletableFuture<Void> submit(Job job, JobContext context) {
        if (scheduler == null) {
            LOG.warn("Scheduler is not started, cannot execute job immediately: job_type={}", job.jobType());
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Scheduler is not started: job_type=" + job.jobType()));
        }

        Future<Void> future = scheduler.submit(new ExecutedJob(job, context, executionHistory, clock), null);

        LOG.debug("Submitted job immediately: job_type={} context={}", job.jobType(), context.parameters());
        return CompletableFutures.from(future, scheduler, DEFAULT_POLLING_DURATION);
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private record ExecutedJob(Job job, JobContext context, JobExecutionHistory history, Clock clock)
            implements Runnable {

        @Override
        public void run() {
            CollectingJobExecutionReport report = new CollectingJobExecutionReport();
            JobContext runContext = context.withReport(report);
            Instant startedAt = clock.instant();

            Duration elapsed;
            JobExecutionStatus status;
            String error;
            try {
                elapsed = Measuring.r(() -> job.execute(runContext));
                if (report.hasFailures()) {
                    status = JobExecutionStatus.FAILURE;
                    error = report.firstFailure().orElse(null);
                } else {
                    status = JobExecutionStatus.SUCCESS;
                    error = null;
                }
            } catch (Throwable t) {
                // Deliberately Throwable, not Exception: anything escaping run() makes
                // scheduleAtFixedRate silently cancel this job for the rest of the process
                // lifetime. A single bad tick must never unschedule a job.
                LOG.error("An error occurred during the job execution: job_type={}", job.jobType(), t);
                elapsed = Duration.between(startedAt, clock.instant());
                status = JobExecutionStatus.FAILURE;
                error = t.toString();
            }

            record(new JobExecution(
                    job.jobType(), startedAt, elapsed, status, report.summary(), report.items(), error));
        }

        private void record(JobExecution execution) {
            try {
                history.add(execution);
            } catch (Throwable t) {
                // Same Throwable-safety as above: a history bug must never unschedule a job
                LOG.error("Failed to record job execution history: job_type={}", execution.jobType(), t);
            }
        }
    }
}
