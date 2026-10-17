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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Schedules the jobs on one small pool; every job visits each project of each workspace.
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

    private static final int DEFAULT_POOL_SIZE = 1;

    private final List<? extends Job> jobs;
    private final int poolSize;
    private final JobLocks jobLocks;

    private ScheduledExecutorService scheduler;

    public PeriodicalScheduler(List<? extends Job> jobs) {
        this(jobs, DEFAULT_POOL_SIZE, new JobLocks());
    }

    public PeriodicalScheduler(List<? extends Job> jobs, int poolSize, JobLocks jobLocks) {
        this.jobs = jobs;
        this.poolSize = poolSize;
        this.jobLocks = jobLocks;
    }

    public void start() {
        if (scheduler == null) {
            scheduler = Executors.newScheduledThreadPool(poolSize, Schedulers.platformThreadfactory("scheduler"));
            for (Job job : jobs) {
                scheduler.scheduleWithFixedDelay(executedJob(job), 0, job.period().toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * Stops the pool and waits for the tick in flight, because a session deletion or a
     * compression rename cut off half-way is exactly the state every job is written to never
     * leave behind.
     */
    @Override
    public void close() {
        if (scheduler == null) {
            return;
        }
        scheduler.shutdownNow();
        try {
            if (!scheduler.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                LOG.warn("Scheduler did not stop within the shutdown timeout: timeout={}", SHUTDOWN_TIMEOUT);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
