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

package cafe.jeffrey.hub.core.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.hub.core.scheduler.Job;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.persistence.api.ProjectsRepository;
import cafe.jeffrey.shared.common.model.job.JobType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
    public void execute(JobContext context) {
        Instant cutoff = clock.instant().minus(retention);
        List<String> purged = projectsRepository.purgeDeletedProjects(cutoff);
        if (!purged.isEmpty()) {
            LOG.info("Purged soft-deleted projects: count={} retention={}", purged.size(), retention);
            context.report().summary("Purged " + purged.size() + " soft-deleted projects");
            for (String projectName : purged) {
                context.report().item("Purged project: " + projectName);
            }
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
