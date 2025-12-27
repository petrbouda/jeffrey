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
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.RepositoryInfo;
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.repository.RecordingStatus;
import pbouda.jeffrey.common.model.repository.RepositoryFile;
import pbouda.jeffrey.common.model.workspace.RepositorySessionInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventCreator;
import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.common.exception.Exceptions;
import pbouda.jeffrey.profile.manager.model.RepositoryStatistics;
import pbouda.jeffrey.profile.manager.model.StreamedRecordingFile;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.profile.parser.chunk.Recordings;
import pbouda.jeffrey.platform.workspace.WorkspaceEventConverter;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class RepositoryManagerImpl implements RepositoryManager {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryManagerImpl.class);

    private static final SupportedRecordingFile TARGET_RECORDING_FILE = SupportedRecordingFile.JFR_LZ4;

    private final Clock clock;
    private final ProjectInfo projectInfo;
    private final Runnable projectsSynchronizerTrigger;
    private final Consumer<String> repositoryCompressionTrigger;
    private final ProjectRepositoryRepository repository;
    private final RemoteRepositoryStorage repositoryStorage;
    private final WorkspaceManager workspaceManager;

    public RepositoryManagerImpl(
            Clock clock,
            ProjectInfo projectInfo,
            Runnable projectsSynchronizerTrigger,
            Consumer<String> repositoryCompressionTrigger,
            ProjectRepositoryRepository repository,
            RemoteRepositoryStorage repositoryStorage,
            WorkspaceManager workspaceManager) {

        this.clock = clock;
        this.projectInfo = projectInfo;
        this.projectsSynchronizerTrigger = projectsSynchronizerTrigger;
        this.repositoryCompressionTrigger = repositoryCompressionTrigger;
        this.repository = repository;
        this.repositoryStorage = repositoryStorage;
        this.workspaceManager = workspaceManager;
    }

    @Override
    public StreamedRecordingFile streamArtifact(String sessionId, String artifactId) {
        // Filter only recording files that are finished and takes all finished files in the session
        Predicate<RepositoryFile> folder = repositoryFile -> {
            return artifactId.equalsIgnoreCase(repositoryFile.id());
        };

        RecordingSession recordingSession = resolveRecordingSession(sessionId);
        List<RepositoryFile> recordingFiles = recordingSession.files().stream()
                .filter(folder)
                .toList();

        return mergeAndStream(sessionId, recordingFiles);
    }

    @Override
    public StreamedRecordingFile mergeAndStreamRecordings(String sessionId, List<String> recordingFileIds) {
        // Filter only recording files that are finished and is contained in the given list of IDs
        Predicate<RepositoryFile> filter = repositoryFile -> {
            return repositoryFile.isRecordingFile()
                    && recordingFileIds.contains(repositoryFile.id())
                    && repositoryFile.status() == RecordingStatus.FINISHED;
        };

        RecordingSession recordingSession = resolveRecordingSession(sessionId);
        List<RepositoryFile> recordingFiles = recordingSession.files().stream()
                .filter(filter)
                .toList();

        return mergeAndStream(sessionId, recordingFiles);
    }

    private RecordingSession resolveRecordingSession(String sessionId) {
        Optional<RecordingSession> sessionOpt = repositoryStorage.singleSession(sessionId, true);
        if (sessionOpt.isEmpty()) {
            throw Exceptions.recordingSessionNotFound(sessionId);
        }
        return sessionOpt.get();
    }

    private StreamedRecordingFile mergeAndStream(String sessionId, List<RepositoryFile> recordingFiles) {
        // Ensure all recording files are compressed before streaming
        List<RepositoryFile> finalRecordingFiles = ensureFilesCompressed(sessionId, recordingFiles);

        // Sum the real size of all files on the filesystem
        long sumOfSizes = finalRecordingFiles.stream()
                .mapToLong(RepositoryFile::size)
                .sum();

        if (finalRecordingFiles.size() == 1) {
            RepositoryFile file = finalRecordingFiles.getFirst();
            return new StreamedRecordingFile(
                    file.name(), sumOfSizes, output -> Recordings.copyByStreaming(file.filePath(), output));
        } else if (finalRecordingFiles.size() > 1) {
            List<Path> files = finalRecordingFiles.stream()
                    .map(RepositoryFile::filePath)
                    .toList();

            RepositoryFile firstRecordingFile = finalRecordingFiles.getFirst();
            String filename = firstRecordingFile.fileType().appendExtension(sessionId);
            return new StreamedRecordingFile(
                    filename, sumOfSizes, output -> Recordings.mergeByStreaming(files, output));
        } else {
            throw Exceptions.emptyRecordingSession(sessionId);
        }
    }

    private List<RepositoryFile> ensureFilesCompressed(String sessionId, List<RepositoryFile> recordingFiles) {
        if (allFilesCompressed(recordingFiles)) {
            return recordingFiles;
        }

        // Trigger compression for the specific session
        repositoryCompressionTrigger.accept(sessionId);

        // Re-fetch files to get updated state
        List<String> fileIds = recordingFiles.stream()
                .map(RepositoryFile::id)
                .toList();

        RecordingSession updatedSession = resolveRecordingSession(sessionId);
        List<RepositoryFile> updatedFiles = updatedSession.files().stream()
                .filter(f -> fileIds.contains(f.id()))
                .toList();

        if (!allFilesCompressed(updatedFiles)) {
            LOG.warn("Not all recording files were compressed after compression trigger: session_id={} file_ids={}",
                    sessionId, fileIds);
            throw Exceptions.compressionError("Cannot stream recordings, not all files are compressed");
        }

        return updatedFiles;
    }

    private boolean allFilesCompressed(List<RepositoryFile> recordingFiles) {
        return recordingFiles.stream()
                .filter(RepositoryFile::isRecordingFile)
                .allMatch(file -> file.fileType() == TARGET_RECORDING_FILE);
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
