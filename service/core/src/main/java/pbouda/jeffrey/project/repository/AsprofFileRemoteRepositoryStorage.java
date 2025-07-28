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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class AsprofFileRemoteRepositoryStorage implements RemoteRepositoryStorage {

    private static final Logger LOG = LoggerFactory.getLogger(AsprofFileRemoteRepositoryStorage.class);

    private final ProjectRepositoryRepository projectRepositoryRepository;
    private final Duration finishedPeriod;
    private final SupportedRecordingFile recordingFileType = SupportedRecordingFile.JFR;

    private record SessionWithLastModified(Path sessionPath, String sessionId, Instant lastModified) {
    }

    public AsprofFileRemoteRepositoryStorage(
            ProjectRepositoryRepository projectRepositoryRepository,
            Duration finishedPeriod) {

        this.projectRepositoryRepository = projectRepositoryRepository;
        this.finishedPeriod = finishedPeriod;
    }

    private Optional<DBRepositoryInfo> repositoryInfo() {
        List<DBRepositoryInfo> repositoryInfos = projectRepositoryRepository.getAll();
        if (repositoryInfos.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(repositoryInfos.getFirst());
    }

    @Override
    public InputStream downloadRecording(String sessionId) {
        Optional<DBRepositoryInfo> repoInfoOpt = repositoryInfo();
        if (repoInfoOpt.isEmpty()) {
            LOG.warn("Repository info is not available");
            return null;
        }

        Path repositoryPath = repoInfoOpt.get().path();
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
    public Optional<RecordingSession> singleSession(String sessionId) {
        return listSessions().stream()
                .filter(session -> session.id().equals(sessionId))
                .findFirst();
    }

    @Override
    public List<RecordingSession> listSessions() {
        return repositoryInfo()
                .map(this::scanAndProcessSessions)
                .orElse(List.of());
    }

    private List<RecordingSession> scanAndProcessSessions(DBRepositoryInfo repositoryInfo) {
        // List all session directories, sorted by last modified time
        List<SessionWithLastModified> sortedSessions = scanSessionDirectories(repositoryInfo.path()).stream()
                .sorted(Comparator.comparing(SessionWithLastModified::lastModified).reversed())
                .toList();

        // Creates RecordingSession objects for each session and marks the latest session as ACTIVE/UNKNOWN
        return IntStream.range(0, sortedSessions.size())
                // First is latest after sorting
                .mapToObj(index -> createRecordingSession(sortedSessions.get(index), repositoryInfo, index == 0))
                .toList();
    }

    private List<SessionWithLastModified> scanSessionDirectories(Path repositoryPath) {
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(repositoryPath)) {
            return StreamSupport.stream(directoryStream.spliterator(), false)
                    .filter(Files::isDirectory)
                    .map(sessionPath -> createSessionInfo(repositoryPath, sessionPath))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
        } catch (IOException e) {
            LOG.error("Failed to read repository directory: {}", repositoryPath, e);
            return List.of();
        }
    }

    private Optional<SessionWithLastModified> createSessionInfo(Path repositoryPath, Path sessionPath) {
        String sessionId = repositoryPath.relativize(sessionPath).toString();
        return directoryModification(sessionPath)
                .map(instant -> new SessionWithLastModified(sessionPath, sessionId, instant));

    }

    private RecordingSession createRecordingSession(
            SessionWithLastModified sessionWithTime,
            DBRepositoryInfo repositoryInfo,
            boolean isLatestSession) {

        // Determine status based on business rule: only latest session can be ACTIVE/UNKNOWN
        RecordingStatus recordingStatus = determineSessionStatus(sessionWithTime, repositoryInfo, isLatestSession);

        List<RepositoryFile> recordings = _listRecordings(
                repositoryInfo,
                recordingStatus,
                repositoryInfo.path(),
                sessionWithTime.sessionPath());

        return createRecordingSession(sessionWithTime.sessionId(), recordings, recordingStatus);
    }

    private RecordingStatus determineSessionStatus(
            SessionWithLastModified sessionWithTime,
            DBRepositoryInfo repositoryInfo,
            boolean isLatestSession) {

        if (isLatestSession) {
            // For latest session, use the strategy-based logic
            StatusStrategy strategy = createStatusStrategy(repositoryInfo);
            return strategy.determineStatus(sessionWithTime.sessionPath());
        } else {
            // For all other sessions, force FINISHED status (business rule)
            return RecordingStatus.FINISHED;
        }
    }

    private RecordingSession createRecordingSession(
            String sessionId, List<RepositoryFile> recordings, RecordingStatus recordingStatus) {

        Instant sessionCreatedAt = null;
        Instant sessionLastModifiedAt = null;
        if (!recordings.isEmpty()) {
            sessionCreatedAt = recordings.getLast().createdAt();
            sessionLastModifiedAt = recordings.getFirst().modifiedAt();
        }

        return new RecordingSession(
                sessionId,
                sessionId,
                sessionCreatedAt,
                sessionLastModifiedAt,
                sessionLastModifiedAt,
                recordingStatus,
                recordingFileType,
                recordings);
    }

    @Override
    public void deleteRepositoryFiles(String sessionId, List<String> repositoryFileIds) {
        Optional<DBRepositoryInfo> repoInfoOpt = repositoryInfo();
        if (repoInfoOpt.isEmpty()) {
            LOG.warn("Repository info is not available");
            return;
        }

        Path repositoryPath = repoInfoOpt.get().path();
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
        Optional<DBRepositoryInfo> repoInfoOpt = repositoryInfo();
        if (repoInfoOpt.isEmpty()) {
            LOG.warn("Repository info is not available");
            return;
        }

        Path repositoryPath = repoInfoOpt.get().path();
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

    private StatusStrategy createStatusStrategy(DBRepositoryInfo repositoryInfo) {
        if (repositoryInfo.finishedSessionDetectionFile() != null) {
            return new WithDetectionFileStrategy(repositoryInfo.finishedSessionDetectionFile(), finishedPeriod);
        } else {
            return new WithoutDetectionFileStrategy(finishedPeriod);
        }
    }

    private static Optional<Instant> directoryModification(Path directory) {
        return sortedFilesInDirectory(directory).stream()
                .filter(file -> Files.isRegularFile(file) && FileSystemUtils.isNotHidden(file))
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

    private List<RepositoryFile> _listRecordings(
            DBRepositoryInfo repositoryInfo, RecordingStatus recordingStatus, Path repositoryPath, Path sessionPath) {

        if (!Files.isDirectory(sessionPath)) {
            LOG.warn("Session directory does not exist: {}", sessionPath);
            return List.of();
        }

        List<RepositoryFile> repositoryFiles = sortedFilesInDirectory(sessionPath).stream()
                .filter(file -> Files.isRegularFile(file) && FileSystemUtils.isNotHidden(file))
                .map(file -> {
                    String sourceId = repositoryPath.relativize(file).toString();
                    String sourceName = sessionPath.relativize(file).toString();
                    Instant modifiedAt = FileSystemUtils.modifiedAt(file);

                    return new RepositoryFile(
                            sourceId,
                            sourceName,
                            FileSystemUtils.createdAt(file),
                            modifiedAt,
                            modifiedAt,
                            FileSystemUtils.size(file),
                            SupportedRecordingFile.of(sourceName),
                            recordingFileType.matches(sourceName),
                            sourceName.equals(repositoryInfo.finishedSessionDetectionFile()),
                            RecordingStatus.FINISHED,
                            file);
                })
                .toList();

        Optional<RepositoryFile> latestRecordingFile = repositoryFiles.stream()
                .filter(RepositoryFile::isRecordingFile)
                .findFirst();

        // Updates the status of the latest recording according to the status of the session.
        if (recordingStatus != RecordingStatus.FINISHED && latestRecordingFile.isPresent()) {
            latestRecordingFile.get().withNonFinishedStatus(recordingStatus);
        }

        return repositoryFiles;
    }

    private interface StatusStrategy {
        RecordingStatus determineStatus(Path sessionPath);
    }

    private static class WithDetectionFileStrategy implements StatusStrategy {
        private final String detectionFileName;
        private final Duration finishedPeriod;

        public WithDetectionFileStrategy(String detectionFileName, Duration finishedPeriod) {
            this.detectionFileName = detectionFileName;
            this.finishedPeriod = finishedPeriod;
        }

        @Override
        public RecordingStatus determineStatus(Path sessionPath) {
            // Check if detection file exists
            Path detectionFile = sessionPath.resolve(detectionFileName);
            if (Files.exists(detectionFile)) {
                return RecordingStatus.FINISHED;
            }

            // Detection file doesn't exist, check timing
            Optional<Instant> modifiedAtOpt = directoryModification(sessionPath);
            if (modifiedAtOpt.isEmpty()) {
                // No Raw Recordings in the Recording Session folder
                return RecordingStatus.UNKNOWN;
            } else if (Instant.now().isAfter(modifiedAtOpt.get().plus(finishedPeriod))) {
                // Latest modification with finished-period passed
                return RecordingStatus.FINISHED;
            } else {
                // Detection file is not present, and the finished-period has not passed
                return RecordingStatus.ACTIVE;
            }
        }
    }

    private static class WithoutDetectionFileStrategy implements StatusStrategy {
        private final Duration finishedPeriod;

        public WithoutDetectionFileStrategy(Duration finishedPeriod) {
            this.finishedPeriod = finishedPeriod;
        }

        @Override
        public RecordingStatus determineStatus(Path sessionPath) {
            Optional<Instant> modifiedAtOpt = directoryModification(sessionPath);
            if (modifiedAtOpt.isEmpty()) {
                // No Raw Recordings in the Recording Session folder
                return RecordingStatus.UNKNOWN;
            } else if (Instant.now().isAfter(modifiedAtOpt.get().plus(finishedPeriod))) {
                // Latest modification with finished-period passed
                return RecordingStatus.FINISHED;
            } else {
                // Finished-period has not passed, but we cannot say it's active because we don't know the detection file
                return RecordingStatus.UNKNOWN;
            }
        }
    }
}
