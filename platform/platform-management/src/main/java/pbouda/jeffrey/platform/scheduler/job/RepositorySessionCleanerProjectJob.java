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

package pbouda.jeffrey.platform.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.shared.common.model.job.JobType;
import pbouda.jeffrey.shared.common.model.repository.RecordingSession;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.platform.scheduler.JobContext;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.platform.scheduler.job.descriptor.RepositorySessionCleanerProjectJobDescriptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public class RepositorySessionCleanerProjectJob extends RepositoryProjectJob<RepositorySessionCleanerProjectJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(RepositorySessionCleanerProjectJob.class);

    private final Duration period;

    public RepositorySessionCleanerProjectJob(
            WorkspacesManager workspacesManager,
            RepositoryStorage.Factory remoteRepositoryManagerFactory,
            JobDescriptorFactory jobDescriptorFactory,
            Duration period) {
        super(workspacesManager, remoteRepositoryManagerFactory, jobDescriptorFactory);
        this.period = period;
    }

    @Override
    protected void executeOnRepository(
            ProjectManager manager,
            RepositoryStorage repositoryStorage,
            RepositorySessionCleanerProjectJobDescriptor jobDescriptor,
            JobContext context) {

        String projectName = manager.info().name();
        LOG.info("Cleaning the repository sessions: project='{}'", projectName);
        Duration duration = jobDescriptor.toDuration();

        Instant currentTime = Instant.now();
        List<RecordingSession> candidatesForDeletion = repositoryStorage.listSessions(false).stream()
                // Sort by created time descending
                .sorted(Comparator.comparing(RecordingSession::createdAt).reversed())
                // Keep at least one session
                .skip(1)
                .filter(session -> currentTime.isAfter(session.createdAt().plus(duration)))
                .toList();

        candidatesForDeletion.forEach(session -> {
            manager.repositoryManager()
                    .deleteRecordingSession(session.id(), WorkspaceEventCreator.REPOSITORY_SESSION_CLEANER_PROJECT_JOB);
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
