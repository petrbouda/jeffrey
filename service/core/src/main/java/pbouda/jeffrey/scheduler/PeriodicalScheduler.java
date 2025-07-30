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

package pbouda.jeffrey.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.Schedulers;
import pbouda.jeffrey.scheduler.job.Job;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PeriodicalScheduler implements Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(PeriodicalScheduler.class);

    private final List<Job> jobs;

    private ScheduledExecutorService scheduler;

    public PeriodicalScheduler(List<Job> jobs) {
        this.jobs = jobs;
    }

    @Override
    public void start() {
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(Schedulers.factory("periodical-scheduler"));
            for (Job job : jobs) {
                scheduler.scheduleAtFixedRate(() -> {
                    // Try-catch handles the exceptions thrown by the tasks and avoids stopping the job.
                    try {
                        job.run();
                    } catch (Exception e) {
                        LOG.error("An error occurred during the job execution: job_type={}", job.jobType(), e);
                    }
                }, 0, job.period().toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
