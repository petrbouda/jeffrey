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

package pbouda.jeffrey.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.repository.RecordingStatus;
import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.scheduler.job.descriptor.RepositoryCompressionProjectJobDescriptor;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Scheduler job that compresses FINISHED JFR files using LZ4 compression.
 * <p>
 * When run without a specific session ID (periodic execution), this job processes:
 * - The ACTIVE session (if any)
 * - The latest FINISHED session
 * <p>
 * Older FINISHED sessions are assumed to be already compressed and are skipped.
 * Only files with status FINISHED are compressed to avoid corrupting active recordings.
 */
public class RepositoryCompressionProjectJob extends RepositoryProjectJob<RepositoryCompressionProjectJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryCompressionProjectJob.class);

    private static final SupportedRecordingFile TYPE_TO_COMPRESS = SupportedRecordingFile.JFR;

    private final SessionFileCompressor sessionFileCompressor;
    private final Duration period;

    public RepositoryCompressionProjectJob(
            WorkspacesManager workspacesManager,
            RemoteRepositoryStorage.Factory remoteRepositoryManagerFactory,
            JobDescriptorFactory jobDescriptorFactory,
            SessionFileCompressor sessionFileCompressor,
            Duration period) {

        super(workspacesManager, remoteRepositoryManagerFactory, jobDescriptorFactory);
        this.sessionFileCompressor = sessionFileCompressor;
        this.period = period;
    }

    @Override
    protected void executeOnRepository(
            ProjectManager manager,
            RemoteRepositoryStorage remoteRepositoryStorage,
            RepositoryCompressionProjectJobDescriptor jobDescriptor) {

        String projectName = manager.info().name();
        LOG.debug("Starting JFR compression check: project='{}'", projectName);

        List<RecordingSession> sessions = remoteRepositoryStorage.listSessions(true);

        if (sessions.isEmpty()) {
            LOG.debug("No sessions found for compression: project='{}'", projectName);
            return;
        }

        // Default behavior: ACTIVE session + latest FINISHED session
        // Find ACTIVE session (if any)
        Optional<RecordingSession> activeSession = sessions.stream()
                .filter(s -> s.status() == RecordingStatus.ACTIVE)
                .findFirst();

        // Find latest FINISHED session by creation date
        Optional<RecordingSession> latestFinishedSession = sessions.stream()
                .filter(s -> s.status() == RecordingStatus.FINISHED)
                .max(Comparator.comparing(RecordingSession::createdAt));

        // Process ACTIVE session (compress only FINISHED files within it)
        activeSession.ifPresent(session ->
                sessionFileCompressor.compressSession(session, projectName, TYPE_TO_COMPRESS));

        // Process latest FINISHED session
        latestFinishedSession.ifPresent(session ->
                sessionFileCompressor.compressSession(session, projectName, TYPE_TO_COMPRESS));
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.REPOSITORY_JFR_COMPRESSION;
    }
}
