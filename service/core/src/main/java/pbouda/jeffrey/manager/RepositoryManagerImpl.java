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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.repository.RecordingStatus;
import pbouda.jeffrey.common.model.repository.RepositoryFile;
import pbouda.jeffrey.common.model.workspace.WorkspaceSessionInfo;
import pbouda.jeffrey.exception.Exceptions;
import pbouda.jeffrey.manager.model.RepositoryStatistics;
import pbouda.jeffrey.manager.model.StreamedRecordingFile;
import pbouda.jeffrey.model.RepositoryInfo;
import pbouda.jeffrey.project.ProjectRepository;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.provider.api.model.DBRepositoryInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.provider.reader.jfr.chunk.Recordings;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class RepositoryManagerImpl implements RepositoryManager {

    private final pbouda.jeffrey.provider.api.repository.ProjectRepository projectRepository;
    private final ProjectRepositoryRepository repository;
    private final RemoteRepositoryStorage repositoryStorage;

    public RepositoryManagerImpl(
            pbouda.jeffrey.provider.api.repository.ProjectRepository projectRepository,
            ProjectRepositoryRepository repository,
            RemoteRepositoryStorage repositoryStorage) {

        this.projectRepository = projectRepository;
        this.repository = repository;
        this.repositoryStorage = repositoryStorage;
    }

    @Override
    public StreamedRecordingFile streamFile(String sessionId, String fileId) {
        // Filter only recording files that are finished and takes all finished files in the session
        Predicate<RepositoryFile> entireSession = repositoryFile -> {
            return fileId.equalsIgnoreCase(repositoryFile.id());
        };

        return mergeAndStream(sessionId, entireSession);
    }

    @Override
    public StreamedRecordingFile streamRecordingFiles(String sessionId, List<String> recordingFileIds) {
        // Filter only recording files that are finished and is contained in the given list of IDs
        Predicate<RepositoryFile> entireSession = repositoryFile -> {
            return repositoryFile.isRecordingFile()
                   && recordingFileIds.contains(repositoryFile.id())
                   && repositoryFile.status() == RecordingStatus.FINISHED;
        };

        return mergeAndStream(sessionId, entireSession);
    }

    private StreamedRecordingFile mergeAndStream(String sessionId, Predicate<RepositoryFile> fileFilter) {
        Optional<RecordingSession> sessionOpt = repositoryStorage.singleSession(sessionId, true);
        if (sessionOpt.isEmpty()) {
            throw Exceptions.recordingSessionNotFound(sessionId);
        }
        RecordingSession session = sessionOpt.get();

        List<RepositoryFile> recordingFiles = session.files().stream()
                .filter(fileFilter)
                .toList();

        // Some the real size of all files on the filesystem
        long sumOfSizes = recordingFiles.stream()
                .mapToLong(RepositoryFile::size)
                .sum();

        String filename;
        Consumer<OutputStream> writer;
        if (recordingFiles.size() == 1) {
            RepositoryFile file = recordingFiles.getFirst();
            filename = file.name();
            writer = output -> Recordings.copyByStreaming(file.filePath(), output);
        } else if (recordingFiles.size() > 1) {
            List<Path> files = recordingFiles.stream()
                    .map(RepositoryFile::filePath)
                    .toList();

            filename = session.recordingFileType().appendExtension(sessionId);
            writer = output -> Recordings.mergeByStreaming(files, output);
        } else {
            throw Exceptions.emptyRecordingSession(sessionId);
        }

        return new StreamedRecordingFile(filename, sumOfSizes, writer);
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
                    case JFR -> jfrFiles++;
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
    public void create(ProjectRepository projectRepository) {
        DBRepositoryInfo dbRepositoryInfo = new DBRepositoryInfo(
                projectRepository.type(), projectRepository.finishedSessionDetectionFile());

        repository.insert(dbRepositoryInfo);
    }

    @Override
    public void createSession(WorkspaceSessionInfo workspaceSessionInfo) {
        projectRepository.createSession(workspaceSessionInfo);
    }

    @Override
    public Optional<RepositoryInfo> info() {
        return repository.getAll().stream()
                .findFirst()
                .map(repository -> {
                    return new RepositoryInfo(
                            repository.type(),
                            repository.finishedSessionDetectionFile());
                });
    }

    @Override
    public void deleteRecordingSession(String recordingSessionId) {
        repositoryStorage.deleteSession(recordingSessionId);
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
