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
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.ExpiredInstanceCleanerJobDescriptor;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.shared.common.model.job.JobType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Deletes dead instances: EXPIRED instances past their retention period, and abandoned
 * PENDING instances (agents that registered but never streamed a session) older than the
 * same retention — no lifecycle event would ever move those out of PENDING.
 *
 * <p>Deleting the row alone is not enough: the instance's directory (with its marker file)
 * must go too, or the workspace reconciler would re-create the instance from disk on its
 * next scan. Row first, directory second — a crash in between briefly resurrects the
 * instance, and this job then deletes it again on a later tick (the loop converges).</p>
 */
public class ExpiredInstanceCleanerJob extends RepositoryProjectJob<ExpiredInstanceCleanerJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(ExpiredInstanceCleanerJob.class);

    private final Duration period;
    private final Clock clock;
    private final HubPlatformRepositories platformRepositories;

    public ExpiredInstanceCleanerJob(
            WorkspacesManager workspacesManager,
            RepositoryStorage.Factory repositoryStorageFactory,
            ExpiredInstanceCleanerJobDescriptor jobDescriptor,
            Duration period,
            Clock clock,
            HubPlatformRepositories platformRepositories) {
        super(workspacesManager, repositoryStorageFactory, jobDescriptor);
        this.period = period;
        this.clock = clock;
        this.platformRepositories = platformRepositories;
    }

    @Override
    protected void executeOnRepository(
            ProjectManager projectManager,
            RepositoryStorage repositoryStorage,
            ExpiredInstanceCleanerJobDescriptor jobDescriptor,
            JobContext context) {

        String projectId = projectManager.info().id();
        String projectName = projectManager.info().name();
        Duration retentionPeriod = jobDescriptor.toDuration();
        Instant currentTime = clock.instant();

        ProjectInstanceRepository instanceRepo = platformRepositories.newProjectInstanceRepository(projectId);

        int deletedCount = 0;
        for (ProjectInstanceInfo instance : instanceRepo.findByStatus(ProjectInstanceStatus.EXPIRED)) {
            if (instance.expiredAt() != null
                    && currentTime.isAfter(instance.expiredAt().plus(retentionPeriod))) {
                deleteInstance(instanceRepo, repositoryStorage, instance.id());
                deletedCount++;
                LOG.info("Deleted expired instance: project='{}' instanceId={} expiredAt={}",
                        projectName, instance.id(), instance.expiredAt());
            }
        }

        if (deletedCount > 0) {
            LOG.debug("Expired instance cleanup completed: project='{}' deleted={}", projectName, deletedCount);
        }

        // An abandoned PENDING instance has no sessions — a session materialization flips the
        // instance to ACTIVE in the same transaction. The session guard is defensive against
        // out-of-order state left behind by older versions.
        Instant startedBefore = currentTime.minus(retentionPeriod);
        int stalePending = 0;
        for (ProjectInstanceInfo instance : instanceRepo.findByStatus(ProjectInstanceStatus.PENDING)) {
            boolean abandoned = instance.startedAt() != null
                    && instance.startedAt().isBefore(startedBefore)
                    && instance.sessionCount() == 0;
            if (abandoned) {
                deleteInstance(instanceRepo, repositoryStorage, instance.id());
                stalePending++;
            }
        }
        if (stalePending > 0) {
            LOG.info("Deleted abandoned PENDING instances: project='{}' count={} retention={}",
                    projectName, stalePending, retentionPeriod);
        }
    }

    private static void deleteInstance(
            ProjectInstanceRepository instanceRepo, RepositoryStorage repositoryStorage, String instanceId) {
        instanceRepo.delete(instanceId);
        try {
            repositoryStorage.deleteInstanceDirectory(instanceId);
        } catch (Exception e) {
            // The reconciler resurrects the instance from the leftover directory and this job
            // deletes it again next tick — log rather than fail the sweep
            LOG.warn("Failed to delete instance directory: instanceId={}", instanceId, e);
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
