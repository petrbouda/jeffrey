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
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.shared.common.model.job.JobType;
import pbouda.jeffrey.shared.common.model.repository.RecordingSession;
import pbouda.jeffrey.shared.common.model.repository.RecordingStatus;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.platform.scheduler.JobContext;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.platform.scheduler.job.descriptor.RepositoryCompressionProjectJobDescriptor;

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

    /**
     * Parameter key for specifying a target session ID for on-demand compression.
     */
    public static final String PARAM_SESSION_ID = "sessionId";

    private final Duration period;

    public RepositoryCompressionProjectJob(
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
            RepositoryCompressionProjectJobDescriptor jobDescriptor,
            JobContext context) {

        String projectName = manager.info().name();
        LOG.debug("Starting JFR compression check: project='{}'", projectName);

        List<RecordingSession> sessions = repositoryStorage.listSessions(true);

        if (sessions.isEmpty()) {
            LOG.debug("No sessions found for compression: project='{}'", projectName);
            return;
        }

        // Check if a specific session ID is provided in context
        Optional<String> targetSessionId = context.get(PARAM_SESSION_ID);

        if (targetSessionId.isPresent()) {
            // Targeted mode: compress files for specific session
            compressSpecificSession(repositoryStorage, sessions, targetSessionId.get(), projectName);
        } else {
            // Default periodic mode: ACTIVE + latest FINISHED sessions
            compressDefaultSessions(repositoryStorage, sessions, projectName);
        }
    }

    private void compressSpecificSession(
            RepositoryStorage repositoryStorage,
            List<RecordingSession> sessions,
            String sessionId,
            String projectName) {

        boolean sessionExists = sessions.stream()
                .anyMatch(s -> s.id().equals(sessionId));

        if (sessionExists) {
            LOG.info("Compressing files for specific session: project='{}' sessionId='{}'", projectName, sessionId);
            repositoryStorage.compressSession(sessionId);
        } else {
            LOG.warn("Target session not found for compression: project='{}' sessionId='{}'", projectName, sessionId);
        }
    }

    private void compressDefaultSessions(
            RepositoryStorage repositoryStorage,
            List<RecordingSession> sessions,
            String projectName) {

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
                repositoryStorage.compressSession(session.id()));

        // Process latest FINISHED session
        latestFinishedSession.ifPresent(session ->
                repositoryStorage.compressSession(session.id()));
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
