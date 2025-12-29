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

import pbouda.jeffrey.shared.model.ProjectInfo;
import pbouda.jeffrey.shared.model.RepositoryInfo;
import pbouda.jeffrey.shared.model.repository.RecordingSession;
import pbouda.jeffrey.shared.model.repository.RecordingStatus;
import pbouda.jeffrey.shared.model.repository.RepositoryFile;
import pbouda.jeffrey.shared.model.workspace.RepositorySessionInfo;
import pbouda.jeffrey.shared.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.model.workspace.WorkspaceEventCreator;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.project.repository.MergedRecording;
import pbouda.jeffrey.platform.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.platform.workspace.WorkspaceEventConverter;
import pbouda.jeffrey.profile.manager.model.RepositoryStatistics;
import pbouda.jeffrey.profile.manager.model.StreamedRecordingFile;
import pbouda.jeffrey.profile.parser.chunk.Recordings;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

public class RepositoryManagerImpl implements RepositoryManager {

    private final Clock clock;
    private final ProjectInfo projectInfo;
    private final Runnable projectsSynchronizerTrigger;
    private final ProjectRepositoryRepository repository;
    private final RemoteRepositoryStorage repositoryStorage;
    private final WorkspaceManager workspaceManager;

    public RepositoryManagerImpl(
            Clock clock,
            ProjectInfo projectInfo,
            Runnable projectsSynchronizerTrigger,
            ProjectRepositoryRepository repository,
            RemoteRepositoryStorage repositoryStorage,
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
        long size = artifactPath.toFile().length();

        return new StreamedRecordingFile(filename, size, output -> Recordings.copyByStreaming(artifactPath, output));
    }

    @Override
    public StreamedRecordingFile mergeAndStreamRecordings(String sessionId, List<String> recordingFileIds) {
        MergedRecording merged = repositoryStorage.mergeRecordings(sessionId, recordingFileIds);
        return new StreamedRecordingFile(
                merged.filename(),
                merged.size(),
                output -> {
                    try (var rec = merged) {
                        Recordings.copyByStreaming(rec.path(), output);
                    }
                }
        );
    }

    @Override
    public Optional<RecordingSession> findRecordingSessions(String recordingSessionId) {
        return repositoryStorage.singleSession(recordingSessionId, true);
    }

    @Override
    public List<RecordingSession> listRecordingSessions(boolean withFiles) {
        return repositoryStorage.listSessions(withFiles);
    }

    @Override
    public RepositoryStatistics calculateRepositoryStatistics() {
        List<RecordingSession> sessions = this.listRecordingSessions(true);

        if (sessions.isEmpty()) {
            return new RepositoryStatistics(
                    0, RecordingStatus.UNKNOWN, 0L, 0L, 0, 0L, 0, 0, 0);
        }

        // Sessions are already sorted by date (newest first) from listSessions()
        RecordingSession latestSession = sessions.getFirst();

        // Calculate aggregated statistics in a single pass
        long totalSize = 0;
        int totalFiles = 0;
        int jfrFiles = 0;
        int heapDumpFiles = 0;
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
                    case HEAP_DUMP -> heapDumpFiles++;
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
                otherFiles
        );
    }

    @Override
    public void create(RepositoryInfo repositoryInfo) {
        repository.insert(repositoryInfo);
    }

    @Override
    public void createSession(RepositorySessionInfo repositorySessionInfo) {
        repository.createSession(repositorySessionInfo);
    }

    @Override
    public Optional<RepositoryInfo> info() {
        return repository.getAll().stream()
                .findFirst();
    }

    @Override
    public void deleteRecordingSession(String recordingSessionId, WorkspaceEventCreator createdBy) {
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
        projectsSynchronizerTrigger.run();
    }

    @Override
    public void deleteFilesInSession(String recordingSessionId, List<String> fileIds) {
        repositoryStorage.deleteRepositoryFiles(recordingSessionId, fileIds);
    }

    @Override
    public void delete() {
        repository.deleteAll();
    }
}
