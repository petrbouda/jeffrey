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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Schedules jobs on two executors split by {@link Job.ExecutorGroup}:
 * {@code GLOBAL} jobs (queue polling, cleaners) get a dedicated single thread,
 * while {@code PROJECT_FAN_OUT} jobs (iterating all workspaces/projects) share
 * a small pool — a slow fan-out can therefore never delay the global jobs.
 *
 * <p>Jobs are scheduled with fixed <em>delay</em> semantics: the period is
 * measured from the end of one run to the start of the next, so a run that
 * overruns its period never produces back-to-back catch-up executions.
 * A per-job lock ({@link JobLocks}) additionally guarantees that the same job never runs
 * concurrently, even when an operator's manual run races a periodic tick.</p>
 */
public class PeriodicalScheduler implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(PeriodicalScheduler.class);

    /** How long {@link #close()} waits for a running tick before letting the context go down under it. */
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

    private static final int DEFAULT_FAN_OUT_POOL_SIZE = 1;

    private final List<? extends Job> jobs;
    private final int fanOutPoolSize;
    private final JobLocks jobLocks;

    private ScheduledExecutorService globalScheduler;
    private ScheduledExecutorService fanOutScheduler;

    public PeriodicalScheduler(List<? extends Job> jobs) {
        this(jobs, DEFAULT_FAN_OUT_POOL_SIZE, new JobLocks());
    }

    public PeriodicalScheduler(List<? extends Job> jobs, int fanOutPoolSize, JobLocks jobLocks) {
        this.jobs = jobs;
        this.fanOutPoolSize = fanOutPoolSize;
        this.jobLocks = jobLocks;
    }

    public void start() {
        if (globalScheduler == null) {
            globalScheduler = Executors.newSingleThreadScheduledExecutor(
                    Schedulers.platformThreadfactory("scheduler-global"));
            fanOutScheduler = Executors.newScheduledThreadPool(
                    fanOutPoolSize, Schedulers.platformThreadfactory("scheduler-fanout"));

            for (Job job : jobs) {
                executorFor(job).scheduleWithFixedDelay(
                        executedJob(job), 0, job.period().toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * Stops both pools and waits for the tick in flight, because the beans a tick works on —
     * the DuckDB provider first of all — are destroyed right after this one, and a session
     * deletion or a compression rename cut off half-way is exactly the state every job is
     * written to never leave behind.
     */
    @Override
    public void close() {
        if (globalScheduler == null) {
            return;
        }
        globalScheduler.shutdownNow();
        fanOutScheduler.shutdownNow();
        awaitTermination(globalScheduler, "global");
        awaitTermination(fanOutScheduler, "fan-out");
    }

    private static void awaitTermination(ExecutorService executor, String name) {
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                LOG.warn("Scheduler did not stop within the shutdown timeout: executor={} timeout={}", name, SHUTDOWN_TIMEOUT);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ScheduledExecutorService executorFor(Job job) {
        return job.executorGroup() == Job.ExecutorGroup.PROJECT_FAN_OUT ? fanOutScheduler : globalScheduler;
    }

    private ExecutedJob executedJob(Job job) {
        return new ExecutedJob(job, jobLocks);
    }

    private record ExecutedJob(Job job, JobLocks locks) implements Runnable {
        @Override
        public void run() {
            // Serializes periodic, on-demand and manual executions of the same job; fixed-delay
            // already prevents periodic self-overlap.
            locks.exclusively(job, () -> {
                try {
                    job.execute();
                } catch (Throwable t) {
                    // Deliberately Throwable, not Exception: anything escaping run() makes
                    // scheduleWithFixedDelay silently cancel this job for the rest of the process
                    // lifetime. A single bad tick must never unschedule a job.
                    LOG.error("An error occurred during the job execution: job_type={}", job.jobType(), t);
                }
                return null;
            });
        }
    }
}
