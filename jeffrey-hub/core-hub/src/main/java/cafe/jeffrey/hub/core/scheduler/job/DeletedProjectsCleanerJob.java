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

package cafe.jeffrey.hub.core.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.hub.core.scheduler.Job;
import cafe.jeffrey.hub.persistence.api.ProjectsRepository;
import cafe.jeffrey.hub.model.job.JobType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Purges soft-deleted project rows once their retention window has passed. A deleted
 * project stays restorable (via its {@code deleted_at} tombstone) for the retention
 * period; after that the tombstone would only accumulate forever, invisible to every
 * query in the application.
 */
public class DeletedProjectsCleanerJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(DeletedProjectsCleanerJob.class);

    private static final String PARAM_RETENTION = "retention";

    private final ProjectsRepository projectsRepository;
    private final Clock clock;
    private final Duration period;
    private final Duration retention;

    public DeletedProjectsCleanerJob(
            ProjectsRepository projectsRepository,
            Clock clock,
            JobConfig config) {

        this.projectsRepository = projectsRepository;
        this.clock = clock;
        this.period = config.period();
        this.retention = config.durationParam(PARAM_RETENTION);
    }

    @Override
    public void execute() {
        Instant cutoff = clock.instant().minus(retention);
        int purged = projectsRepository.purgeDeletedProjects(cutoff);
        if (purged > 0) {
            LOG.info("Purged soft-deleted projects: count={} retention={}", purged, retention);
        }
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.DELETED_PROJECTS_CLEANER;
    }
}
