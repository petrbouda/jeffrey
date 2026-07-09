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
import cafe.jeffrey.shared.common.model.job.JobType;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PeriodicalScheduler implements Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(PeriodicalScheduler.class);

    private static final Duration DEFAULT_POLLING_DURATION = Duration.ofMillis(10);

    /**
     * A small pool so that one slow job (e.g. a per-project fan-out doing remote storage I/O)
     * cannot head-of-line block the latency-sensitive jobs like the event replicator.
     */
    private static final int SCHEDULER_POOL_SIZE = 4;

    /**
     * Spreads the very first execution of the jobs after startup, instead of firing
     * all of them at t=0 and running a thundering herd through the pool.
     */
    private static final Duration STARTUP_STAGGER = Duration.ofMillis(500);

    private final List<? extends Job> jobs;
    private final ConcurrentMap<JobType, Lock> jobLocks = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;

    public PeriodicalScheduler(List<? extends Job> jobs) {
        this.jobs = jobs;
    }

    @Override
    public void start() {
        if (scheduler == null) {
            scheduler = Executors.newScheduledThreadPool(
                    SCHEDULER_POOL_SIZE, Schedulers.platformThreadfactory("periodical-scheduler"));

            long stagger = 0;
            for (Job job : jobs) {
                scheduler.scheduleAtFixedRate(
                        new ExecutedJob(job, JobContext.EMPTY, lockFor(job)),
                        stagger, job.period().toMillis(), TimeUnit.MILLISECONDS);
                stagger += STARTUP_STAGGER.toMillis();
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

        Future<Void> future = scheduler.submit(new ExecutedJob(job, context, lockFor(job)), null);

        LOG.debug("Submitted job immediately: job_type={} context={}", job.jobType(), context.parameters());
        return CompletableFutures.from(future, scheduler, DEFAULT_POLLING_DURATION);
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /**
     * With more than one worker thread, the periodic run of a job and an on-demand
     * {@link #submit} of the same job could overlap. Jobs are written as single-writer
     * loops (e.g. the projects synchronizer advances a queue offset), so each job type
     * is serialized on its own lock; distinct jobs still run in parallel.
     */
    private Lock lockFor(Job job) {
        return jobLocks.computeIfAbsent(job.jobType(), _ -> new ReentrantLock());
    }

    private record ExecutedJob(Job job, JobContext context, Lock lock) implements Runnable {
        @Override
        public void run() {
            lock.lock();
            try {
                job.execute(context);
            } catch (Throwable t) {
                // Deliberately Throwable, not Exception: anything escaping run() makes
                // scheduleAtFixedRate silently cancel this job for the rest of the process
                // lifetime. A single bad tick must never unschedule a job.
                LOG.error("An error occurred during the job execution: job_type={}", job.jobType(), t);
            } finally {
                lock.unlock();
            }
        }
    }
}
