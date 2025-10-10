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
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.RepositoryType;
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.repository.RecordingStatus;
import pbouda.jeffrey.common.model.repository.RepositoryFile;
import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.common.model.workspace.WorkspaceSessionInfo;
import pbouda.jeffrey.project.repository.detection.StatusStrategy;
import pbouda.jeffrey.project.repository.detection.WithDetectionFileStrategy;
import pbouda.jeffrey.project.repository.detection.WithoutDetectionFileStrategy;
import pbouda.jeffrey.project.repository.file.FileInfoProcessor;
import pbouda.jeffrey.provider.api.model.DBRepositoryInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepository;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

public class AsprofFileRemoteRepositoryStorage implements RemoteRepositoryStorage {

    private static final Logger LOG = LoggerFactory.getLogger(AsprofFileRemoteRepositoryStorage.class);

    private final ProjectInfo projectInfo;
    private final HomeDirs homeDirs;
    private final ProjectRepository projectRepository;
    private final ProjectRepositoryRepository projectRepositoryRepository;
    private final FileInfoProcessor fileInfoProcessor;
    private final Duration finishedPeriod;
    private final Clock clock;
    private final SupportedRecordingFile recordingFileType = SupportedRecordingFile.JFR;

    public AsprofFileRemoteRepositoryStorage(
            ProjectInfo projectInfo,
            HomeDirs homeDirs,
            ProjectRepository projectRepository,
            ProjectRepositoryRepository projectRepositoryRepository,
            FileInfoProcessor fileInfoProcessor,
            Duration finishedPeriod,
            Clock clock) {

        this.projectInfo = projectInfo;
        this.projectRepository = projectRepository;
        this.homeDirs = homeDirs;
        this.projectRepositoryRepository = projectRepositoryRepository;
        this.fileInfoProcessor = fileInfoProcessor;
        this.finishedPeriod = finishedPeriod;
        this.clock = clock;
    }

    private DBRepositoryInfo repositoryInfo() {
        List<DBRepositoryInfo> repositoryInfos = projectRepositoryRepository.getAll();
        if (repositoryInfos.isEmpty()) {
            throw new IllegalStateException("No repository info found for project: " + projectInfo.id());
        }
        return repositoryInfos.getFirst();
    }

    private Path resolveWorkspacePath(WorkspaceSessionInfo sessionInfo) {
        Path workspacesPath = sessionInfo.workspacesPath();
        Path resolvedWorkspacesPath = workspacesPath == null ? homeDirs.workspaces() : workspacesPath;
        return resolvedWorkspacesPath.resolve(sessionInfo.repositoryId());
    }

    private Path resolveSessionPath(WorkspaceSessionInfo sessionInfo) {
        Path workspacePath = resolveWorkspacePath(sessionInfo);
        return workspacePath.resolve(sessionInfo.relativePath());
    }

    @Override
    public Optional<RecordingSession> singleSession(String sessionId, boolean withFiles) {
        List<WorkspaceSessionInfo> sessions = projectRepository.findAllSessions();

        if (sessions.isEmpty()) {
            LOG.warn("No sessions found for project: {}", projectInfo.id());
            return Optional.empty();
        }

        // is session latest by original creation date?
        boolean isLatestSession = sessions.stream()
                .max(Comparator.comparing(WorkspaceSessionInfo::originCreatedAt))
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
        List<WorkspaceSessionInfo> sessions = projectRepository.findAllSessions().stream()
                .sorted(Comparator.comparing(WorkspaceSessionInfo::originCreatedAt).reversed())
                .toList();

        // Creates RecordingSession objects for each session and marks the latest session as ACTIVE/UNKNOWN
        return IntStream.range(0, sessions.size())
                // First is latest after sorting
                .mapToObj(index -> createRecordingSession(withFiles, sessions.get(index), index == 0))
                .toList();
    }

    private RecordingSession createRecordingSession(
            boolean withFiles, WorkspaceSessionInfo sessionInfo, boolean isLatestSession) {

        DBRepositoryInfo repositoryInfo = repositoryInfo();

        Path workspacePath = resolveWorkspacePath(sessionInfo);
        Path sessionPath = workspacePath.resolve(sessionInfo.relativePath());

        // Determine status based on business rule: only latest session can be ACTIVE/UNKNOWN
        RecordingStatus recordingStatus = determineSessionStatus(sessionPath, repositoryInfo, isLatestSession);

        List<RepositoryFile> repositoryFiles;
        if (withFiles) {
            repositoryFiles = _listRepositoryFiles(
                    repositoryInfo,
                    recordingStatus,
                    workspacePath,
                    sessionPath);
        } else {
            repositoryFiles = List.of();
        }

        return new RecordingSession(
                sessionInfo.sessionId(),
                sessionInfo.sessionId(),
                sessionInfo.originCreatedAt(),
                recordingStatus,
                recordingFileType,
                repositoryFiles);
    }

    private RecordingStatus determineSessionStatus(
            Path sessionPath,
            DBRepositoryInfo repositoryInfo,
            boolean isLatestSession) {

        if (isLatestSession) {
            // For latest session, use the strategy-based logic
            StatusStrategy strategy = createStatusStrategy(repositoryInfo);
            return strategy.determineStatus(sessionPath);
        } else {
            // For all other sessions, force FINISHED status (business rule)
            return RecordingStatus.FINISHED;
        }
    }

    @Override
    public void deleteRepositoryFiles(String sessionId, List<String> sessionFileIds) {
        Optional<WorkspaceSessionInfo> workspaceSessionOpt =
                projectRepository.findSessionById(sessionId);

        if (workspaceSessionOpt.isEmpty()) {
            LOG.warn("Session not found for project {}: {}", projectInfo.id(), sessionId);
            return;
        }
        WorkspaceSessionInfo sessionInfo = workspaceSessionOpt.get();

        Path sessionPath = resolveSessionPath(sessionInfo);
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
        Optional<WorkspaceSessionInfo> workspaceSessionOpt =
                projectRepository.findSessionById(sessionId);

        if (workspaceSessionOpt.isEmpty()) {
            LOG.warn("Session not found for project {}: {}", projectInfo.id(), sessionId);
            return;
        }
        WorkspaceSessionInfo sessionInfo = workspaceSessionOpt.get();

        Path sessionPath = resolveSessionPath(sessionInfo);
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

    protected StatusStrategy createStatusStrategy(DBRepositoryInfo repositoryInfo) {
        if (repositoryInfo.finishedSessionDetectionFile() != null) {
            return new WithDetectionFileStrategy(repositoryInfo.finishedSessionDetectionFile(), finishedPeriod, clock);
        } else {
            return new WithoutDetectionFileStrategy(finishedPeriod, clock);
        }
    }

    private List<RepositoryFile> _listRepositoryFiles(
            DBRepositoryInfo repositoryInfo,
            RecordingStatus recordingStatus,
            Path workspacePath,
            Path sessionPath) {

        if (!Files.isDirectory(sessionPath)) {
            LOG.warn("Session directory does not exist: {}", sessionPath);
            return List.of();
        }

        List<RepositoryFile> repositoryFiles = FileSystemUtils.sortedFilesInDirectory(
                        sessionPath, fileInfoProcessor.comparator()).stream()
                .filter(FileSystemUtils::isNotHidden)
                .map(file -> {
                    String sourceId = workspacePath.relativize(file).toString();
                    String sourceName = sessionPath.relativize(file).toString();

                    return new RepositoryFile(
                            sourceId,
                            sourceName,
                            fileInfoProcessor.createdAt(file),
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
}
