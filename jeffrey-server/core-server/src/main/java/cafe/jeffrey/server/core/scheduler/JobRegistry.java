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

package cafe.jeffrey.server.core.scheduler;

import cafe.jeffrey.server.core.configuration.properties.SchedulerJobsProperties;
import cafe.jeffrey.shared.common.model.job.JobInfo;
import cafe.jeffrey.shared.common.model.job.JobType;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces the current view of all configured scheduler jobs by joining
 * {@link JobType} metadata with the resolved {@link SchedulerJobsProperties}.
 * Used by the read-only scheduler REST endpoint.
 */
public class JobRegistry {

    private final SchedulerJobsProperties schedulerJobsProperties;

    public JobRegistry(SchedulerJobsProperties schedulerJobsProperties) {
        this.schedulerJobsProperties = schedulerJobsProperties;
    }

    public List<JobInfo> all() {
        List<JobInfo> result = new ArrayList<>();
        for (JobType jobType : JobType.values()) {
            SchedulerJobsProperties.JobConfig config = schedulerJobsProperties.forType(jobType);
            result.add(new JobInfo(
                    jobType,
                    jobType.executionLevel(),
                    config.period(),
                    config.params(),
                    config.enabled()));
        }
        return result;
    }
}
