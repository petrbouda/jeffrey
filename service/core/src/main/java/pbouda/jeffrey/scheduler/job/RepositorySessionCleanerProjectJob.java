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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.scheduler.job.descriptor.RepositorySessionCleanerJobDescriptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class RepositorySessionCleanerProjectJob extends RepositoryProjectJob<RepositorySessionCleanerJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(RepositorySessionCleanerProjectJob.class);
    private final Duration period;

    public RepositorySessionCleanerProjectJob(
            WorkspacesManager workspacesManager,
            SchedulerManager schedulerManager,
            RemoteRepositoryStorage.Factory remoteRepositoryManagerFactory,
            JobDescriptorFactory jobDescriptorFactory,
            Duration period) {
        super(workspacesManager, schedulerManager, remoteRepositoryManagerFactory, jobDescriptorFactory);
        this.period = period;
    }

    protected void executeOnRepository(
            ProjectManager manager,
            RemoteRepositoryStorage remoteRepositoryStorage,
            RepositorySessionCleanerJobDescriptor jobDescriptor) {

        String projectName = manager.info().name();
        LOG.info("Cleaning the repository sessions: project='{}'", projectName);
        Duration duration = jobDescriptor.toDuration();

        Instant currentTime = Instant.now();
        List<RecordingSession> candidatesForDeletion = remoteRepositoryStorage.listSessions(false).stream()
                .filter(session -> currentTime.isAfter(session.createdAt().plus(duration)))
                .toList();

        candidatesForDeletion.forEach(session -> {
            remoteRepositoryStorage.deleteSession(session.id());
            LOG.info("Deleted recording from the repository: project='{}' session={}", projectName, session.id());
        });
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.REPOSITORY_SESSION_CLEANER;
    }
}
