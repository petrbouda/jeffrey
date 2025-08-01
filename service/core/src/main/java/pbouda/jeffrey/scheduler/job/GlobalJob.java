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

package pbouda.jeffrey.scheduler.job;

import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.common.model.job.JobInfo;
import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptor;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;

import java.time.Duration;
import java.util.List;

public abstract class GlobalJob<T extends JobDescriptor<T>> extends Job {

    private final SchedulerManager schedulerManager;
    private final JobDescriptorFactory jobDescriptorFactory;

    public GlobalJob(
            SchedulerManager schedulerManager,
            JobDescriptorFactory jobDescriptorFactory,
            JobType jobType,
            Duration period) {

        super(jobType, period);
        this.schedulerManager = schedulerManager;
        this.jobDescriptorFactory = jobDescriptorFactory;
    }

    @Override
    public void run() {
        List<JobInfo> allJobs = schedulerManager.all(jobType());
        for (JobInfo jobInfo : allJobs) {
            if (jobInfo.enabled()) {
                T jobDescriptor = jobDescriptorFactory.create(jobInfo);
                execute(jobDescriptor);
            }
        }
    }

    protected abstract void execute(T jobInfo);
}
