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

import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.hub.model.job.JobType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

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
public class ExpiredInstanceCleanerJob extends RepositoryProjectJob {

    private static final Logger LOG = LoggerFactory.getLogger(ExpiredInstanceCleanerJob.class);

    private static final String PARAM_RETENTION = "retention";

    private final Duration period;
    private final Duration retentionPeriod;
    private final Clock clock;

    public ExpiredInstanceCleanerJob(WorkspacesManager workspacesManager, JobConfig config, Clock clock) {
        super(workspacesManager);
        this.period = config.period();
        this.retentionPeriod = config.durationParam(PARAM_RETENTION);
        this.clock = clock;
    }

    @Override
    protected void executeOnRepository(ProjectManager projectManager, RepositoryStorage repositoryStorage) {
        String projectName = projectManager.info().name();
        Instant currentTime = clock.instant();

        ProjectInstanceRepository instanceRepo = projectManager.projectInstanceRepository();

        int deletedCount = 0;
        for (ProjectInstanceInfo instance : instanceRepo.findByStatus(ProjectInstanceStatus.EXPIRED)) {
            if (instance.expiredAt() != null
                    && currentTime.isAfter(instance.expiredAt().plus(retentionPeriod))) {
                deleteInstance(instanceRepo, repositoryStorage, instance.id());
                deletedCount++;
                LOG.info("Deleted expired instance: project_name={} instance_id={} expired_at={}",
                        projectName, instance.id(), instance.expiredAt());
            }
        }

        if (deletedCount > 0) {
            LOG.debug("Expired instance cleanup completed: project_name={} deleted={}", projectName, deletedCount);
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
            LOG.info("Deleted abandoned PENDING instances: project_name={} count={} retention={}",
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
            LOG.warn("Failed to delete instance directory: instance_id={}", instanceId, e);
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
