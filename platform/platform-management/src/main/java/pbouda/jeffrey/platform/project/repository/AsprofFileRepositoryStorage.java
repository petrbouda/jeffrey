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

package pbouda.jeffrey.platform.project.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.platform.project.repository.detection.StatusStrategy;
import pbouda.jeffrey.platform.project.repository.detection.WithDetectionFileStrategy;
import pbouda.jeffrey.platform.project.repository.detection.WithoutDetectionFileStrategy;
import pbouda.jeffrey.platform.project.repository.file.FileInfoProcessor;
import pbouda.jeffrey.provider.platform.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.shared.common.compression.Lz4Compressor;
import pbouda.jeffrey.shared.common.exception.Exceptions;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.RepositoryType;
import pbouda.jeffrey.shared.common.model.repository.RecordingSession;
import pbouda.jeffrey.shared.common.model.repository.RecordingStatus;
import pbouda.jeffrey.shared.common.model.repository.RepositoryFile;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

public class AsprofFileRepositoryStorage implements RepositoryStorage {

    private static final Logger LOG = LoggerFactory.getLogger(AsprofFileRepositoryStorage.class);

    public static final String STREAMING_REPO_DIR = "streaming-repo";

    // JFR_LZ4 must come first so removeExtension matches longer extension first (.jfr.lz4 before .jfr)
    private static final List<SupportedRecordingFile> RECORDING_FILE_TYPES =
            List.of(SupportedRecordingFile.JFR_LZ4, SupportedRecordingFile.JFR);

    private static final List<String> RECORDING_EXTENSIONS = RECORDING_FILE_TYPES.stream()
            .map(SupportedRecordingFile::fileExtension)
            .toList();

    private static final SupportedRecordingFile TARGET_COMPRESSED_TYPE = SupportedRecordingFile.JFR_LZ4;

    private final Lock compressionLock = new ReentrantLock();
    private final Clock clock;
    private final ProjectInfo projectInfo;
    private final JeffreyDirs jeffreyDirs;
    private final ProjectRepositoryRepository projectRepositoryRepository;
    private final FileInfoProcessor fileInfoProcessor;
    private final Duration finishedPeriod;
    private final RecordingFileEventEmitter eventEmitter;

    public AsprofFileRepositoryStorage(
            Clock clock,
            ProjectInfo projectInfo,
            JeffreyDirs jeffreyDirs,
            ProjectRepositoryRepository projectRepositoryRepository,
            FileInfoProcessor fileInfoProcessor,
            Duration finishedPeriod,
            RecordingFileEventEmitter eventEmitter) {

        this.clock = clock;
        this.projectInfo = projectInfo;
        this.jeffreyDirs = jeffreyDirs;
        this.projectRepositoryRepository = projectRepositoryRepository;
        this.fileInfoProcessor = fileInfoProcessor;
        this.finishedPeriod = finishedPeriod;
        this.eventEmitter = eventEmitter;
    }

    @Override
    public RepositoryInfo repositoryInfo() {
        List<RepositoryInfo> repositoryInfos = projectRepositoryRepository.getAll();
        if (repositoryInfos.isEmpty()) {
            throw new IllegalStateException("No repository info found for project: " + projectInfo.id());
        }
        return repositoryInfos.getFirst();
    }

    private Path resolveWorkspacePath(RepositoryInfo repositoryInfo) {
        String workspacesPath = repositoryInfo.workspacesPath();
        Path resolvedWorkspacesPath = workspacesPath == null ? jeffreyDirs.workspaces() : Path.of(workspacesPath);
        return resolvedWorkspacesPath
                .resolve(repositoryInfo.relativeWorkspacePath());
    }

    private Path resolveSessionPath(RepositoryInfo repositoryInfo, ProjectInstanceSessionInfo sessionInfo) {
        return resolveWorkspacePath(repositoryInfo)
                .resolve(repositoryInfo.relativeProjectPath())
                .resolve(sessionInfo.relativeSessionPath());
    }

    @Override
    public Optional<RecordingSession> singleSession(String sessionId, boolean withFiles) {
        List<ProjectInstanceSessionInfo> sessions = projectRepositoryRepository.findAllSessions();

        if (sessions.isEmpty()) {
            LOG.warn("No sessions found for project: {}", projectInfo.id());
            return Optional.empty();
        }

        // is session latest by original creation date?
        boolean isLatestSession = sessions.stream()
                .max(Comparator.comparing(ProjectInstanceSessionInfo::originCreatedAt))
                .map(latestSession -> latestSession.sessionId().equals(sessionId))
                .orElse(false);

        // Find the session with the given sessionId
        return sessions.stream()
                .filter(session -> session.sessionId().equals(sessionId))
                .map(session -> createRecordingSession(withFiles, session, isLatestSession))
                .findFirst();
    }

    @Override
    public List<RecordingSession> listSessions(boolean withFiles) {
        List<ProjectInstanceSessionInfo> sessions = projectRepositoryRepository.findAllSessions().stream()
                .sorted(Comparator.comparing(ProjectInstanceSessionInfo::originCreatedAt).reversed())
                .toList();

        // Creates RecordingSession objects for each session and marks the latest session as ACTIVE/UNKNOWN
        return IntStream.range(0, sessions.size())
                // First is latest after sorting
                .mapToObj(index -> createRecordingSession(withFiles, sessions.get(index), index == 0))
                .toList();
    }

    private RecordingSession createRecordingSession(
            boolean withFiles, ProjectInstanceSessionInfo sessionInfo, boolean isLatestSession) {

        RepositoryInfo repositoryInfo = repositoryInfo();

        Path workspacePath = resolveWorkspacePath(repositoryInfo);
        Path sessionPath = workspacePath
                .resolve(repositoryInfo.relativeProjectPath())
                .resolve(sessionInfo.relativeSessionPath());

        // Determine status based on business rule: only latest session can be ACTIVE/UNKNOWN
        RecordingStatus recordingStatus = determineSessionStatus(sessionPath, sessionInfo, isLatestSession);

        List<RepositoryFile> repositoryFiles;
        if (withFiles) {
            repositoryFiles = _listRepositoryFiles(
                    sessionInfo,
                    recordingStatus,
                    workspacePath,
                    sessionPath);
        } else {
            repositoryFiles = List.of();
        }

        return new RecordingSession(
                sessionInfo.sessionId(),
                sessionInfo.relativeSessionPath().toString(),
                sessionInfo.instanceId(),
                sessionInfo.originCreatedAt(),
                recordingStatus,
                sessionInfo.profilerSettings(),
                sessionPath,
                sessionPath.resolve(STREAMING_REPO_DIR),
                repositoryFiles);
    }

    private RecordingStatus determineSessionStatus(
            Path sessionPath,
            ProjectInstanceSessionInfo sessionInfo,
            boolean isLatestSession) {

        if (isLatestSession) {
            // For latest session, use the strategy-based logic
            StatusStrategy strategy = createStatusStrategy(sessionInfo);
            return strategy.determineStatus(sessionPath);
        } else {
            // For all other sessions, force FINISHED status (business rule)
            return RecordingStatus.FINISHED;
        }
    }

    @Override
    public void deleteRepositoryFiles(String sessionId, List<String> sessionFileIds) {
        RepositoryInfo repositoryInfo = repositoryInfo();

        Optional<ProjectInstanceSessionInfo> workspaceSessionOpt =
                projectRepositoryRepository.findSessionById(sessionId);

        if (workspaceSessionOpt.isEmpty()) {
            LOG.warn("Session not found for project {}: {}", projectInfo.id(), sessionId);
            return;
        }
        ProjectInstanceSessionInfo sessionInfo = workspaceSessionOpt.get();

        Path sessionPath = resolveSessionPath(repositoryInfo, sessionInfo);
        if (!Files.isDirectory(sessionPath)) {
            LOG.warn("Session directory does not exist: {}", sessionPath);
            return;
        }

        for (String sessionFileId : sessionFileIds) {
            // Repository file ID is relative to the workspace path
            // e.g. "projectId/sessionId/recording.jfr"
            Path repositoryFile = sessionPath.resolve(sessionFileId);
            FileSystemUtils.removeFile(repositoryFile);
        }

        LOG.info("Deleted files in repository session: session={} file_ids={}", sessionPath, sessionFileIds);
    }

    @Override
    public void deleteSession(String sessionId) {
        RepositoryInfo repositoryInfo = repositoryInfo();

        Optional<ProjectInstanceSessionInfo> workspaceSessionOpt =
                projectRepositoryRepository.findSessionById(sessionId);

        if (workspaceSessionOpt.isEmpty()) {
            LOG.warn("Session not found for project {}: {}", projectInfo.id(), sessionId);
            return;
        }
        ProjectInstanceSessionInfo sessionInfo = workspaceSessionOpt.get();

        Path sessionPath = resolveSessionPath(repositoryInfo, sessionInfo);
        if (!Files.isDirectory(sessionPath)) {
            LOG.warn("Session directory does not exist: {}", sessionPath);
            return;
        }

        FileSystemUtils.removeDirectory(sessionPath);
        LOG.info("Deleted session directory: {}", sessionPath);
    }

    @Override
    public RepositoryType type() {
        return RepositoryType.ASYNC_PROFILER;
    }

    protected StatusStrategy createStatusStrategy(ProjectInstanceSessionInfo sessionInfo) {
        if (sessionInfo.finishedFile() != null) {
            return new WithDetectionFileStrategy(sessionInfo.finishedFile(), finishedPeriod, clock);
        } else {
            return new WithoutDetectionFileStrategy(finishedPeriod, clock);
        }
    }

    // ========== Recording Files ==========

    @Override
    public List<Path> recordings(String sessionId) {
        return recordings(sessionId, null);
    }

    @Override
    public List<Path> recordings(String sessionId, List<String> recordingIds) {
        RecordingSession session = resolveSession(sessionId);

        return session.files().stream()
                .filter(file -> Files.isRegularFile(file.filePath()))
                .filter(RepositoryFile::isRecordingFile)
                .filter(file -> file.status() == RecordingStatus.FINISHED)
                .filter(file -> recordingIds == null || recordingIds.contains(file.id()))
                .map(file -> ensureCompressed(sessionId, file))
                .distinct()
                .toList();
    }

    // ========== Merge Recordings ==========

    @Override
    public MergedRecording mergeRecordings(String sessionId) {
        return mergeRecordings(sessionId, null);
    }

    @Override
    public MergedRecording mergeRecordings(String sessionId, List<String> recordingIds) {
        List<Path> compressedPaths = recordings(sessionId, recordingIds);

        if (compressedPaths.isEmpty()) {
            throw Exceptions.emptyRecordingSession(sessionId);
        }

        // Create merged file with .jfr extension (not .jfr.lz4)
        // because we decompress LZ4 files and concatenate raw JFR content.
        // JFR format natively supports multiple chunks concatenated.
        Path tempFile = jeffreyDirs.temp().resolve(SupportedRecordingFile.JFR.appendExtension(sessionId));

        // Decompress each LZ4 file and merge the raw JFR content
        try (OutputStream out = Files.newOutputStream(tempFile,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (Path compressed : compressedPaths) {
                if (Lz4Compressor.isLz4Compressed(compressed)) {
                    Lz4Compressor.decompressTo(compressed, out);
                } else {
                    Files.copy(compressed, out);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to merge recordings: " + compressedPaths, e);
        }

        // Fallback for empty merged file
        if (FileSystemUtils.size(tempFile) <= 0) {
            LOG.warn("Merged recording is empty: {}", tempFile);
        }

        return new MergedRecording(tempFile);
    }

    // ========== Artifact Files ==========

    @Override
    public List<Path> artifacts(String sessionId) {
        return artifacts(sessionId, null);
    }

    @Override
    public List<Path> artifacts(String sessionId, List<String> artifactIds) {
        RecordingSession session = resolveSession(sessionId);

        return session.files().stream()
                .filter(file -> Files.isRegularFile(file.filePath()))
                .filter(file -> !file.isRecordingFile())
                .filter(file -> artifactIds == null || artifactIds.contains(file.id()))
                .map(RepositoryFile::filePath)
                .toList();
    }

    // ========== Session Compression ==========

    @Override
    public int compressSession(String sessionId) {
        RecordingSession session = resolveSession(sessionId);

        return (int) session.files().stream()
                .filter(RepositoryFile::isRecordingFile)
                .filter(file -> file.status() == RecordingStatus.FINISHED)
                .map(file -> ensureCompressed(sessionId, file))
                .distinct()
                .count();
    }

    // ========== Private Helpers ==========

    private RecordingSession resolveSession(String sessionId) {
        Optional<RecordingSession> sessionOpt = singleSession(sessionId, true);
        if (sessionOpt.isEmpty()) {
            throw Exceptions.recordingSessionNotFound(sessionId);
        }
        return sessionOpt.get();
    }

    /**
     * Ensures the recording file is compressed (JFR_LZ4 format).
     * <p>
     * If already compressed, returns the original path. Otherwise, compresses the file
     * and stores the compressed version persistently in the same directory.
     * Uses double-check locking pattern for thread safety.
     * Emits RECORDING_FILE_CREATED event when actual compression happens.
     * </p>
     */
    private Path ensureCompressed(String sessionId, RepositoryFile file) {
        if (file.fileType() == TARGET_COMPRESSED_TYPE) {
            return file.filePath();
        }

        Path sourcePath = file.filePath();
        Path compressedPath = sourcePath.resolveSibling(file.name() + ".lz4");

        // Fast path: check if already compressed by another thread
        if (Files.exists(compressedPath)) {
            FileSystemUtils.removeFile(sourcePath);
            return compressedPath;
        }

        compressionLock.lock();
        try {
            // Double-check after acquiring lock
            if (Files.exists(compressedPath)) {
                FileSystemUtils.removeFile(sourcePath);
                return compressedPath;
            }

            // Capture original file size before compression
            long originalSize = Files.size(sourcePath);

            // Compress, verify, and delete original
            Lz4Compressor.compress(sourcePath, compressedPath);
            long compressedSize = Files.size(compressedPath);
            if (Files.exists(compressedPath) && compressedSize > 0) {
                FileSystemUtils.removeFile(sourcePath);

                // Emit event - only when actual compression happened and file stored
                eventEmitter.emitRecordingFileCreated(projectInfo, sessionId, file, originalSize, compressedSize, compressedPath);
            }
            return compressedPath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to verify compressed file: " + compressedPath, e);
        } finally {
            compressionLock.unlock();
        }
    }

    private List<RepositoryFile> _listRepositoryFiles(
            ProjectInstanceSessionInfo sessionInfo,
            RecordingStatus recordingStatus,
            Path workspacePath,
            Path sessionPath) {

        if (!Files.isDirectory(sessionPath)) {
            LOG.warn("Session directory does not exist: {}", sessionPath);
            return List.of();
        }

        List<RepositoryFile> repositoryFiles = FileSystemUtils.sortedFilesInDirectory(
                        sessionPath, fileInfoProcessor.comparator()).stream()
                .filter(Files::isRegularFile)
                .filter(FileSystemUtils::isNotHidden)
                .map(file -> {
                    String sourceId = FileSystemUtils.removeExtension(
                            workspacePath.relativize(file), RECORDING_EXTENSIONS);

                    String sourceName = sessionPath.relativize(file).toString();
                    boolean isRecordingFile = RECORDING_FILE_TYPES.stream()
                            .anyMatch(type -> type.matches(sourceName));

                    return new RepositoryFile(
                            sourceId,
                            sourceName,
                            fileInfoProcessor.createdAt(file),
                            FileSystemUtils.size(file),
                            SupportedRecordingFile.of(sourceName),
                            isRecordingFile,
                            sourceName.equals(sessionInfo.finishedFile()),
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
}
