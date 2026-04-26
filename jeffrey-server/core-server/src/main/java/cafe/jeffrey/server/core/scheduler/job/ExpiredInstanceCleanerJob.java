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

package cafe.jeffrey.server.core.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.server.core.manager.project.ProjectManager;
import cafe.jeffrey.server.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.server.core.scheduler.JobContext;
import cafe.jeffrey.server.core.scheduler.job.descriptor.ExpiredInstanceCleanerJobDescriptor;
import cafe.jeffrey.server.core.scheduler.job.descriptor.JobDescriptorFactory;
import cafe.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import cafe.jeffrey.server.persistence.repository.ProjectInstanceRepository;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.shared.common.model.job.JobType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Deletes EXPIRED instance rows that have been expired longer than the configured retention period.
 */
public class ExpiredInstanceCleanerJob extends ProjectJob<ExpiredInstanceCleanerJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(ExpiredInstanceCleanerJob.class);

    private final Duration period;
    private final Clock clock;
    private final ServerPlatformRepositories platformRepositories;

    public ExpiredInstanceCleanerJob(
            WorkspacesManager workspacesManager,
            JobDescriptorFactory jobDescriptorFactory,
            Duration period,
            Clock clock,
            ServerPlatformRepositories platformRepositories) {
        super(workspacesManager, jobDescriptorFactory);
        this.period = period;
        this.clock = clock;
        this.platformRepositories = platformRepositories;
    }

    @Override
    protected void execute(ProjectManager projectManager, ExpiredInstanceCleanerJobDescriptor jobDescriptor, JobContext context) {
        String projectId = projectManager.info().id();
        String projectName = projectManager.info().name();
        Duration retentionPeriod = jobDescriptor.toDuration();
        Instant currentTime = clock.instant();

        ProjectInstanceRepository instanceRepo = platformRepositories.newProjectInstanceRepository(projectId);
        List<ProjectInstanceInfo> expiredInstances = instanceRepo.findByStatus(ProjectInstanceStatus.EXPIRED);

        int deletedCount = 0;
        for (ProjectInstanceInfo instance : expiredInstances) {
            if (instance.expiredAt() != null
                    && currentTime.isAfter(instance.expiredAt().plus(retentionPeriod))) {
                instanceRepo.delete(instance.id());
                deletedCount++;
                LOG.info("Deleted expired instance: project='{}' instanceId={} expiredAt={}",
                        projectName, instance.id(), instance.expiredAt());
            }
        }

        if (deletedCount > 0) {
            LOG.debug("Expired instance cleanup completed: project='{}' deleted={}", projectName, deletedCount);
        }
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.EXPIRED_INSTANCE_CLEANER;
    }
}
