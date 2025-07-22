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

package pbouda.jeffrey.scheduler.task;

import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.provider.api.model.job.JobInfo;
import pbouda.jeffrey.provider.api.model.job.JobType;

import java.time.Duration;
import java.util.List;

public abstract class GlobalJob extends Job {

    private final SchedulerManager schedulerManager;

    public GlobalJob(SchedulerManager schedulerManager, JobType jobType, Duration period) {
        super(jobType, period);
        this.schedulerManager = schedulerManager;
    }

    @Override
    public void run() {
        List<JobInfo> allJobs = schedulerManager.all(jobType());
        for (JobInfo jobInfo : allJobs) {
            if (jobInfo.enabled()) {
                execute(jobInfo);
            }
        }
    }

    protected abstract void execute(JobInfo jobInfo);
}
