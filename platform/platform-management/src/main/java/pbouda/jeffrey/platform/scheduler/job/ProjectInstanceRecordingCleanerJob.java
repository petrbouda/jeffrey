/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
import pbouda.jeffrey.shared.common.model.job.JobType;
import pbouda.jeffrey.shared.common.model.repository.RecordingSession;
import pbouda.jeffrey.shared.common.model.repository.RecordingStatus;
import pbouda.jeffrey.shared.common.model.repository.RepositoryFile;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.platform.scheduler.JobContext;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.platform.scheduler.job.descriptor.ProjectInstanceRecordingCleanerJobDescriptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ProjectInstanceRecordingCleanerJob extends RepositoryProjectJob<ProjectInstanceRecordingCleanerJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectInstanceRecordingCleanerJob.class);
    private final Duration period;

    public ProjectInstanceRecordingCleanerJob(
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
            ProjectInstanceRecordingCleanerJobDescriptor jobDescriptor,
            JobContext context) {

        String projectName = manager.info().name();
        LOG.info("Cleaning the project instance recordings: project='{}'", projectName);
        Duration duration = jobDescriptor.toDuration();

        // Find the active session (newest one with ACTIVE status, or just the newest)
        Optional<RecordingSession> activeSession = repositoryStorage.listSessions(false).stream()
                .filter(session -> session.status() == RecordingStatus.ACTIVE)
                .max(Comparator.comparing(RecordingSession::createdAt));

        if (activeSession.isEmpty()) {
            return;
        }

        RecordingSession session = activeSession.get();

        // Get the session with files
        Optional<RecordingSession> sessionWithFiles = repositoryStorage.singleSession(session.id(), true);
        if (sessionWithFiles.isEmpty()) {
            LOG.warn("Active session not found when fetching files: project='{}' session={}", projectName, session.id());
            return;
        }

        Instant currentTime = Instant.now();

        // Find recording files older than the retention period that are finished
        List<String> filesToDelete = sessionWithFiles.get().files().stream()
                .filter(RepositoryFile::isRecordingFile)
                .filter(RepositoryFile::isFinished)
                .filter(file -> currentTime.isAfter(file.createdAt().plus(duration)))
                .map(RepositoryFile::id)
                .toList();

        if (filesToDelete.isEmpty()) {
            return;
        }

        repositoryStorage.deleteRepositoryFiles(session.id(), filesToDelete);
        LOG.info("Deleted {} recordings from active session: project='{}' session={}",
                filesToDelete.size(), projectName, session.id());
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.PROJECT_INSTANCE_RECORDING_CLEANER;
    }
}
