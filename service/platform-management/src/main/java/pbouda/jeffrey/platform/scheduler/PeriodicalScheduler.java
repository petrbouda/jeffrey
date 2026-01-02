/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.platform.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.CompletableFutures;
import pbouda.jeffrey.shared.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

public class PeriodicalScheduler implements Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(PeriodicalScheduler.class);

    private static final Duration DEFAULT_POLLING_DURATION = Duration.ofMillis(10);

    private final List<? extends Job> jobs;

    private ScheduledExecutorService scheduler;

    public PeriodicalScheduler(List<? extends Job> jobs) {
        this.jobs = jobs;
    }

    @Override
    public void start() {
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(
                    Schedulers.platformThreadfactory("periodical-scheduler"));

            for (Job job : jobs) {
                scheduler.scheduleAtFixedRate(
                        new ExecutedJob(job, JobContext.EMPTY), 0, job.period().toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public CompletableFuture<Void> submit(Job job, JobContext context) {
        if (scheduler == null) {
            LOG.warn("Scheduler is not started, cannot execute job immediately: job_type={}", job.jobType());
            return null;
        }

        Future<Void> future = scheduler.submit(new ExecutedJob(job, context), null);

        LOG.debug("Submitted job immediately: job_type={} context={}", job.jobType(), context.parameters());
        return CompletableFutures.from(future, scheduler, DEFAULT_POLLING_DURATION);
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private record ExecutedJob(Job job, JobContext context) implements Runnable {
        @Override
        public void run() {
            try {
                job.execute(context);
            } catch (Exception e) {
                LOG.error("An error occurred during the job execution: job_type={}", job.jobType(), e);
            }
        }
    }
}
