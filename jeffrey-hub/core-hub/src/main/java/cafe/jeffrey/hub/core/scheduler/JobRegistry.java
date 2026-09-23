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

import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties;
import cafe.jeffrey.hub.model.job.JobInfo;
import cafe.jeffrey.hub.model.job.JobType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Produces the current view of all configured scheduler jobs by joining
 * {@link JobType} metadata with the resolved {@link SchedulerJobsProperties}.
 * Used by the read-only scheduler REST endpoint.
 */
public class JobRegistry {

    private final SchedulerJobsProperties schedulerJobsProperties;
    private final Set<JobType> manuallyTriggerable;

    public JobRegistry(SchedulerJobsProperties schedulerJobsProperties, Set<JobType> manuallyTriggerable) {
        this.schedulerJobsProperties = schedulerJobsProperties;
        this.manuallyTriggerable = manuallyTriggerable;
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
                    config.enabled(),
                    manuallyTriggerable.contains(jobType)));
        }
        return result;
    }
}
