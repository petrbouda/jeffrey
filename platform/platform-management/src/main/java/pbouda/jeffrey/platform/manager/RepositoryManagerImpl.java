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

package pbouda.jeffrey.platform.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.project.repository.MergedRecording;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.platform.scheduler.SchedulerTrigger;
import pbouda.jeffrey.platform.workspace.WorkspaceEventConverter;
import pbouda.jeffrey.profile.manager.model.RepositoryStatistics;
import pbouda.jeffrey.profile.manager.model.StreamedRecordingFile;
import pbouda.jeffrey.provider.platform.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.repository.RecordingSession;
import pbouda.jeffrey.shared.common.model.repository.RecordingStatus;
import pbouda.jeffrey.shared.common.model.repository.RepositoryFile;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class RepositoryManagerImpl implements RepositoryManager {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryManagerImpl.class);

    private final Clock clock;
    private final ProjectInfo projectInfo;
    private final SchedulerTrigger projectsSynchronizerTrigger;
    private final ProjectRepositoryRepository repository;
    private final RepositoryStorage repositoryStorage;
    private final WorkspaceManager workspaceManager;

    public RepositoryManagerImpl(
            Clock clock,
            ProjectInfo projectInfo,
            SchedulerTrigger projectsSynchronizerTrigger,
            ProjectRepositoryRepository repository,
            RepositoryStorage repositoryStorage,
            WorkspaceManager workspaceManager) {

        this.clock = clock;
        this.projectInfo = projectInfo;
        this.projectsSynchronizerTrigger = projectsSynchronizerTrigger;
        this.repository = repository;
        this.repositoryStorage = repositoryStorage;
        this.workspaceManager = workspaceManager;
    }

    @Override
    public StreamedRecordingFile streamArtifact(String sessionId, String artifactId) {
        List<Path> artifactPaths = repositoryStorage.artifacts(sessionId, List.of(artifactId));

        if (artifactPaths.isEmpty()) {
            throw new IllegalArgumentException("Artifact not found: session_id=" + sessionId + " artifact_id=" + artifactId);
        }

        Path artifactPath = artifactPaths.getFirst();
        String filename = artifactPath.getFileName().toString();

        return new StreamedRecordingFile(filename, artifactPath);
    }

    @Override
    public StreamedRecordingFile mergeAndStreamRecordings(String sessionId, List<String> recordingFileIds) {
        LOG.debug("Merging and streaming recordings: sessionId={} fileCount={}", sessionId, recordingFileIds.size());
        long startTime = System.nanoTime();
        MergedRecording merged = repositoryStorage.mergeRecordings(sessionId, recordingFileIds);
        LOG.debug("Merging and streaming recordings completed: sessionId={} durationMs={}", sessionId, Duration.ofNanos(System.nanoTime() - startTime).toMillis());
        return new StreamedRecordingFile(merged.filename(), merged.path(), merged::close);
    }

    @Override
    public Optional<RecordingSession> findRecordingSessions(String recordingSessionId) {
        return repositoryStorage.singleSession(recordingSessionId, true);
    }

    @Override
    public List<RecordingSession> listRecordingSessions(boolean withFiles) {
        LOG.debug("Listing recording sessions: withFiles={}", withFiles);
        return repositoryStorage.listSessions(withFiles);
    }

    @Override
    public RepositoryStatistics calculateRepositoryStatistics() {
        LOG.debug("Calculating repository statistics");
        List<RecordingSession> sessions = this.listRecordingSessions(true);

        if (sessions.isEmpty()) {
            return new RepositoryStatistics(
                    0, RecordingStatus.UNKNOWN, 0L, 0L, 0, 0L, 0, 0, 0, 0, 0);
        }

        // Sessions are already sorted by date (newest first) from listSessions()
        RecordingSession latestSession = sessions.getFirst();

        // Calculate aggregated statistics in a single pass
        long totalSize = 0;
        int totalFiles = 0;
        int jfrFiles = 0;
        int heapDumpFiles = 0;
        int logFiles = 0;
        int errorLogFiles = 0;
        int otherFiles = 0;
        long biggestSessionSize = 0;

        for (RecordingSession session : sessions) {
            long sessionSize = 0;

            for (RepositoryFile file : session.files()) {
                totalFiles++;
                long fileSize = file.size() != null ? file.size() : 0L;
                totalSize += fileSize;
                sessionSize += fileSize;

                // Count by file type
                switch (file.fileType()) {
                    case JFR, JFR_LZ4 -> jfrFiles++;
                    case HEAP_DUMP, HEAP_DUMP_GZ -> heapDumpFiles++;
                    case JVM_LOG -> logFiles++;
                    case HS_JVM_ERROR_LOG -> errorLogFiles++;
                    default -> otherFiles++;
                }
            }

            biggestSessionSize = Math.max(biggestSessionSize, sessionSize);
        }

        // Find the most recent file timestamp across all sessions (true last activity)
        long lastActivityTime = 0L;
        for (RecordingSession session : sessions) {
            for (RepositoryFile file : session.files()) {
                if (file.createdAt() != null) {
                    long fileTime = file.createdAt().toEpochMilli();
                    lastActivityTime = Math.max(lastActivityTime, fileTime);
                }
            }
        }

        return new RepositoryStatistics(
                sessions.size(),
                latestSession.status(),
                lastActivityTime,
                totalSize,
                totalFiles,
                biggestSessionSize,
                jfrFiles,
                heapDumpFiles,
                logFiles,
                errorLogFiles,
                otherFiles
        );
    }

    @Override
    public void create(RepositoryInfo repositoryInfo) {
        repository.insert(repositoryInfo);
    }

    @Override
    public void createSession(ProjectInstanceSessionInfo projectInstanceSessionInfo) {
        repository.createSession(projectInstanceSessionInfo);
    }

    @Override
    public Optional<RepositoryInfo> info() {
        return repository.getAll().stream()
                .findFirst();
    }

    @Override
    public void deleteRecordingSession(String recordingSessionId, WorkspaceEventCreator createdBy) {
        LOG.debug("Deleting recording session: sessionId={}", recordingSessionId);
        WorkspaceEvent workspaceEvent = WorkspaceEventConverter.sessionDeleted(
                clock.instant(),
                projectInfo.workspaceId(),
                projectInfo.id(),
                recordingSessionId,
                createdBy);

        workspaceManager
                .workspaceEventManager()
                .batchInsertEvents(List.of(workspaceEvent));

        // Trigger event synchronization
        projectsSynchronizerTrigger.execute();
    }

    @Override
    public void deleteFilesInSession(String recordingSessionId, List<String> fileIds) {
        repositoryStorage.deleteRepositoryFiles(recordingSessionId, fileIds);
    }

    @Override
    public void delete() {
        LOG.debug("Deleting repository");
        repository.deleteAll();
    }
}
