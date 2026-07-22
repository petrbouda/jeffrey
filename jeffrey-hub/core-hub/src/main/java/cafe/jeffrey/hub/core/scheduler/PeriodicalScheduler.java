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
import cafe.jeffrey.shared.common.CompletableFutures;
import cafe.jeffrey.shared.common.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Schedules jobs on two executors split by {@link Job.ExecutorGroup}:
 * {@code GLOBAL} jobs (queue polling, cleaners) get a dedicated single thread,
 * while {@code PROJECT_FAN_OUT} jobs (iterating all workspaces/projects) share
 * a small pool — a slow fan-out can therefore never delay the global jobs.
 *
 * <p>Jobs are scheduled with fixed <em>delay</em> semantics: the period is
 * measured from the end of one run to the start of the next, so a run that
 * overruns its period never produces back-to-back catch-up executions.
 * A per-job lock additionally guarantees that the same job never runs
 * concurrently, even when an on-demand {@link #submit} races a periodic tick
 * on the fan-out pool.</p>
 */
public class PeriodicalScheduler implements Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(PeriodicalScheduler.class);

    private static final Duration DEFAULT_POLLING_DURATION = Duration.ofMillis(10);
    private static final int DEFAULT_FAN_OUT_POOL_SIZE = 1;

    private final List<? extends Job> jobs;
    private final int fanOutPoolSize;
    private final ConcurrentMap<Job, ReentrantLock> jobLocks = new ConcurrentHashMap<>();

    private ScheduledExecutorService globalScheduler;
    private ScheduledExecutorService fanOutScheduler;

    public PeriodicalScheduler(List<? extends Job> jobs) {
        this(jobs, DEFAULT_FAN_OUT_POOL_SIZE);
    }

    public PeriodicalScheduler(List<? extends Job> jobs, int fanOutPoolSize) {
        this.jobs = jobs;
        this.fanOutPoolSize = fanOutPoolSize;
    }

    @Override
    public void start() {
        if (globalScheduler == null) {
            globalScheduler = Executors.newSingleThreadScheduledExecutor(
                    Schedulers.platformThreadfactory("scheduler-global"));
            fanOutScheduler = Executors.newScheduledThreadPool(
                    fanOutPoolSize, Schedulers.platformThreadfactory("scheduler-fanout"));

            for (Job job : jobs) {
                executorFor(job).scheduleWithFixedDelay(
                        executedJob(job, JobContext.EMPTY), 0, job.period().toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public CompletableFuture<Void> submit(Job job, JobContext context) {
        if (globalScheduler == null) {
            LOG.warn("Scheduler is not started, cannot execute job immediately: job_type={}", job.jobType());
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Scheduler is not started: job_type=" + job.jobType()));
        }

        ScheduledExecutorService executor = executorFor(job);
        Future<Void> future = executor.submit(executedJob(job, context), null);

        LOG.debug("Submitted job immediately: job_type={} context={}", job.jobType(), context.parameters());
        return CompletableFutures.from(future, executor, DEFAULT_POLLING_DURATION);
    }

    @Override
    public void close() {
        if (globalScheduler != null) {
            globalScheduler.shutdown();
            fanOutScheduler.shutdown();
        }
    }

    private ScheduledExecutorService executorFor(Job job) {
        return job.executorGroup() == Job.ExecutorGroup.PROJECT_FAN_OUT ? fanOutScheduler : globalScheduler;
    }

    private ExecutedJob executedJob(Job job, JobContext context) {
        ReentrantLock lock = jobLocks.computeIfAbsent(job, j -> new ReentrantLock());
        return new ExecutedJob(job, context, lock);
    }

    private record ExecutedJob(Job job, JobContext context, ReentrantLock lock) implements Runnable {
        @Override
        public void run() {
            // Serializes periodic and on-demand executions of the same job on the
            // fan-out pool; fixed-delay already prevents periodic self-overlap.
            lock.lock();
            try {
                job.execute(context);
            } catch (Throwable t) {
                // Deliberately Throwable, not Exception: anything escaping run() makes
                // scheduleWithFixedDelay silently cancel this job for the rest of the process
                // lifetime. A single bad tick must never unschedule a job.
                LOG.error("An error occurred during the job execution: job_type={}", job.jobType(), t);
            } finally {
                lock.unlock();
            }
        }
    }
}
