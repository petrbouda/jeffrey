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

package pbouda.jeffrey.project.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.common.model.RepositoryType;
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.repository.RecordingStatus;
import pbouda.jeffrey.common.model.repository.RepositoryFile;
import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.provider.api.model.DBRepositoryInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class AsprofFileRemoteRepositoryStorage implements RemoteRepositoryStorage {

    private static final Logger LOG = LoggerFactory.getLogger(AsprofFileRemoteRepositoryStorage.class);

    private final String projectId;
    private final ProjectRepositoryRepository projectRepositoryRepository;
    private final Duration finishedPeriod;
    private final SupportedRecordingFile recordingFileType = SupportedRecordingFile.JFR;

    public AsprofFileRemoteRepositoryStorage(
            String projectId,
            ProjectRepositoryRepository projectRepositoryRepository,
            Duration finishedPeriod) {

        this.projectId = projectId;
        this.projectRepositoryRepository = projectRepositoryRepository;
        this.finishedPeriod = finishedPeriod;
    }

    private DBRepositoryInfo repositoryInfo() {
        List<DBRepositoryInfo> repositoryInfos = projectRepositoryRepository.getAll();
        if (repositoryInfos.isEmpty()) {
            throw new IllegalStateException("No linked repository found for project: project_id=" + projectId);
        }
        return repositoryInfos.getFirst();
    }

    @Override
    public InputStream downloadRecording(String sessionId) {
        DBRepositoryInfo repoInfo = repositoryInfo();
        Path repositoryPath = repoInfo.path();
        Path recordingPath = repositoryPath.resolve(sessionId);

        if (!Files.isRegularFile(recordingPath)) {
            LOG.warn("Recording file does not exist: {}", recordingPath);
            return null;
        }

        try {
            return Files.newInputStream(recordingPath);
        } catch (IOException e) {
            LOG.error("Failed to open recording file: {}", recordingPath, e);
            return null;
        }
    }

    @Override
    public List<RepositoryFile> listRepositoryFiles(String sessionId) {
        DBRepositoryInfo repoInfo = repositoryInfo();
        Path repositoryPath = repoInfo.path();
        Path sessionPath = repositoryPath.resolve(sessionId);

        RecordingStatus recordingStatus = recordingSessionStatus(repoInfo, sessionPath);
        return _listRecordings(recordingStatus, repositoryPath, sessionPath);
    }

    @Override
    public List<RecordingSession> listSessions() {
        DBRepositoryInfo repoInfo = repositoryInfo();
        Path repositoryPath = repoInfo.path();
        List<RecordingSession> sessions = new ArrayList<>();

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(repositoryPath)) {
            for (Path sessionPath : directoryStream) {
                if (Files.isDirectory(sessionPath)) {
                    RecordingStatus recordingStatus = recordingSessionStatus(repoInfo, sessionPath);

                    String sessionId = repositoryPath.relativize(sessionPath).toString();
                    List<RepositoryFile> recordings =
                            _listRecordings(recordingStatus, repositoryPath, sessionPath);

                    Instant sessionCreatedAt = null;
                    Instant sessionLastModifiedAt = null;
                    if (!recordings.isEmpty()) {
                        sessionCreatedAt = recordings.getLast().createdAt();
                        sessionLastModifiedAt = recordings.getFirst().modifiedAt();
                    }

                    RecordingSession session = new RecordingSession(
                            sessionId,
                            sessionId,
                            sessionCreatedAt,
                            sessionLastModifiedAt,
                            sessionLastModifiedAt,
                            recordingStatus,
                            recordingFileType,
                            recordings);

                    sessions.add(session);
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to read repository directory: {}", repositoryPath, e);
        }

        return sessions;
    }

    @Override
    public void deleteRepositoryFiles(String sessionId, List<String> repositoryFileIds) {
        DBRepositoryInfo repoInfo = repositoryInfo();
        Path repositoryPath = repoInfo.path();
        Path sessionPath = repositoryPath.resolve(sessionId);

        if (!Files.isDirectory(sessionPath)) {
            LOG.warn("Session directory does not exist: {}", sessionPath);
            return;
        }

        for (String repositoryFileId : repositoryFileIds) {
            // Repository file ID is relative to the repository path
            // e.g. "sessionId/recording.jfr"
            Path repositoryFile = repositoryPath.resolve(repositoryFileId);
            FileSystemUtils.removeFile(repositoryFile);
        }

        LOG.info("Deleted files in repository session: session={} file_ids={}", sessionPath, repositoryFileIds);
    }

    @Override
    public void deleteSession(String sessionId) {
        DBRepositoryInfo repoInfo = repositoryInfo();
        Path repositoryPath = repoInfo.path();
        Path sessionPath = repositoryPath.resolve(sessionId);

        if (!Files.isDirectory(sessionPath)) {
            LOG.warn("Session directory does not exist: {}", sessionPath);
            return;
        }

        FileSystemUtils.removeDirectory(sessionPath);
        LOG.info("Deleted session directory: {}", sessionPath);
    }

    @Override
    public SupportedRecordingFile supportedRecordingFileType() {
        return recordingFileType;
    }

    @Override
    public RepositoryType type() {
        return RepositoryType.ASYNC_PROFILER;
    }

    private RecordingStatus recordingSessionStatus(DBRepositoryInfo repositoryInfo, Path sessionPath) {
        // Repository has a detection file, and it's been already generated in the Recording Session folder
        if (repositoryInfo.finishedSessionDetectionFile() != null) {
            Path detectionFile = sessionPath.resolve(repositoryInfo.finishedSessionDetectionFile());
            if (Files.exists(detectionFile)) {
                return RecordingStatus.FINISHED;
            }

            Optional<Instant> modifiedAtOpt = directoryModification(sessionPath);
            if (modifiedAtOpt.isEmpty()) {
                // No Raw Recordings in the Recording Session folder
                return RecordingStatus.UNKNOWN;
            } else if (Instant.now().isAfter(modifiedAtOpt.get().plus(finishedPeriod))) {
                // Latest modification with finished-period
                // (period after which the recording is identified as finished) passed
                return RecordingStatus.FINISHED;
            } else {
                // Detection file is not present, and the finished-period has not passed
                return RecordingStatus.ACTIVE;
            }
        }

        Optional<Instant> modifiedAtOpt = directoryModification(sessionPath);
        if (modifiedAtOpt.isEmpty()) {
            // No Raw Recordings in the Recording Session folder
            return RecordingStatus.UNKNOWN;
        } else if (Instant.now().isAfter(modifiedAtOpt.get().plus(finishedPeriod))) {
            // Latest modification with finished-period
            // (period after which the recording is identified as finished) passed
            return RecordingStatus.FINISHED;
        } else {
            // Finished-period has not passed, but we cannot say it's active because we don't know the detection file.'
            return RecordingStatus.UNKNOWN;
        }
    }

    private static Optional<Instant> directoryModification(Path directory) {
        return sortedFilesInDirectory(directory).stream()
                .findFirst()
                .map(FileSystemUtils::modifiedAt);
    }

    private static List<Path> sortedFilesInDirectory(Path directory) {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .sorted(Comparator.comparing(FileSystemUtils::modifiedAt).reversed())
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read directory: " + directory, e);
        }
    }

    private List<RepositoryFile> _listRecordings(RecordingStatus recordingStatus, Path repositoryPath, Path sessionPath) {
        if (!Files.isDirectory(sessionPath)) {
            LOG.warn("Session directory does not exist: {}", sessionPath);
            return List.of();
        }

        List<RepositoryFile> repositoryFiles = sortedFilesInDirectory(sessionPath).stream()
                .filter(file -> Files.isRegularFile(file) && FileSystemUtils.isNotHidden(file))
                .map(file -> {
                    String sourceId = repositoryPath.relativize(file).toString();
                    String sourceName = sessionPath.relativize(file).toString();
                    long size = FileSystemUtils.size(file);
                    Instant modifiedAt = FileSystemUtils.modifiedAt(file);

                    return new RepositoryFile(
                            sourceId,
                            sourceName,
                            FileSystemUtils.createdAt(file),
                            modifiedAt,
                            modifiedAt,
                            size,
                            SupportedRecordingFile.of(sourceName),
                            recordingFileType.matches(sourceName),
                            RecordingStatus.FINISHED,
                            file);
                })
                .toList();

        // Updates the status of the latest recording according to the status of the session.
        if (recordingStatus != RecordingStatus.FINISHED && !repositoryFiles.isEmpty()) {
            RepositoryFile updatedRecording = repositoryFiles.getFirst().withNonFinishedStatus(recordingStatus);

            List<RepositoryFile> mutableList = new ArrayList<>(repositoryFiles);
            mutableList.set(0, updatedRecording);
            return mutableList;
        }

        return repositoryFiles;
    }
}
