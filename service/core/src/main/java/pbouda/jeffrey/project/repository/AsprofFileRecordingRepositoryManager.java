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
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.provider.api.model.DBRepositoryInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AsprofFileRecordingRepositoryManager implements RecordingRepositoryManager {

    private static final Logger LOG = LoggerFactory.getLogger(AsprofFileRecordingRepositoryManager.class);

    private final ProjectInfo projectInfo;
    private final ProjectRepositoryRepository projectRepositoryRepository;

    public AsprofFileRecordingRepositoryManager(
            ProjectInfo projectInfo, ProjectRepositoryRepository projectRepositoryRepository) {

        this.projectInfo = projectInfo;
        this.projectRepositoryRepository = projectRepositoryRepository;
    }

    @Override
    public InputStream downloadRecording(String recordingId) {
        List<DBRepositoryInfo> repositoryInfo = projectRepositoryRepository.getAll();
        if (repositoryInfo.isEmpty()) {
            LOG.warn("No repositories linked to project: project_id={}", projectInfo.id());
            return null;
        }

        DBRepositoryInfo repoInfo = repositoryInfo.getFirst();
        Path repositoryPath = repoInfo.path();

        // Recording ID format is expected to be "sessionId/sourceId"
        String[] parts = recordingId.split("/", 2);
        if (parts.length != 2) {
            LOG.warn("Invalid recording ID format: {}", recordingId);
            return null;
        }

        String sessionId = parts[0];
        String sourceId = parts[1];

        Path sessionPath = repositoryPath.resolve(sessionId);
        Path sourcePath = sessionPath.resolve(sourceId);

        if (!Files.isRegularFile(sourcePath)) {
            LOG.warn("Recording file does not exist: {}", sourcePath);
            return null;
        }

        try {
            return Files.newInputStream(sourcePath);
        } catch (IOException e) {
            LOG.error("Failed to open recording file: {}", sourcePath, e);
            return null;
        }
    }

    @Override
    public List<RecordingSource> listRecordings(String sessionId) {
        List<DBRepositoryInfo> repositoryInfo = projectRepositoryRepository.getAll();
        if (repositoryInfo.isEmpty()) {
            LOG.warn("No repositories linked to project: project_id={}", projectInfo.id());
            return List.of();
        }

        DBRepositoryInfo repoInfo = repositoryInfo.getFirst();
        Path repositoryPath = repoInfo.path();
        Path sessionPath = repositoryPath.resolve(sessionId);

        if (!Files.isDirectory(sessionPath)) {
            LOG.warn("Session directory does not exist: {}", sessionPath);
            return List.of();
        }

        List<RecordingSource> sources = new ArrayList<>();

        try (DirectoryStream<Path> sourcesStream = Files.newDirectoryStream(sessionPath)) {
            for (Path sourcePath : sourcesStream) {
                if (Files.isRegularFile(sourcePath)) {
                    String sourceId = repositoryPath.relativize(sourcePath).toString();
                    String sourceName = sessionPath.relativize(sourcePath).toString();
                    Instant createdAt = Files.getLastModifiedTime(sourcePath).toInstant();
                    Instant lastModifiedAt = createdAt;
                    long size = Files.size(sourcePath);

                    boolean isFileOpen = isFileOpenedByAnotherProcess(sourcePath);
                    RecordingStatus status = isFileOpen ? RecordingStatus.IN_PROGRESS : RecordingStatus.FINISHED;

                    RecordingSource source = new RecordingSource(
                            sourceId,
                            sourceName,
                            createdAt,
                            lastModifiedAt,
                            isFileOpen ? null : lastModifiedAt,
                            size,
                            status);

                    sources.add(source);
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to read sources in session directory: {}", sessionPath, e);
        }

        return sources;
    }

    @Override
    public void deleteRecording(String recordingId) {
        List<DBRepositoryInfo> repositoryInfo = projectRepositoryRepository.getAll();
        if (repositoryInfo.isEmpty()) {
            LOG.warn("No repositories linked to project: project_id={}", projectInfo.id());
            return;
        }

        DBRepositoryInfo repoInfo = repositoryInfo.getFirst();
        Path repositoryPath = repoInfo.path();

        // Recording ID format is expected to be "sessionId/sourceId"
        String[] parts = recordingId.split("/", 2);
        if (parts.length != 2) {
            LOG.warn("Invalid recording ID format: {}", recordingId);
            return;
        }

        String sessionId = parts[0];
        String sourceId = parts[1];

        Path sessionPath = repositoryPath.resolve(sessionId);
        Path sourcePath = sessionPath.resolve(sourceId);

        if (!Files.isRegularFile(sourcePath)) {
            LOG.warn("Recording file does not exist: {}", sourcePath);
            return;
        }

        try {
            Files.delete(sourcePath);
            LOG.info("Deleted recording file: {}", sourcePath);
        } catch (IOException e) {
            LOG.error("Failed to delete recording file: {}", sourcePath, e);
        }
    }

    @Override
    public List<RecordingSession> listSessions() {
        List<DBRepositoryInfo> repositoryInfos = projectRepositoryRepository.getAll();
        if (repositoryInfos.isEmpty()) {
            LOG.warn("No repositories linked to project: project_id={}", projectInfo.id());
            return List.of();
        }

        DBRepositoryInfo repositoryInfo = repositoryInfos.getFirst();
        Path repositoryPath = repositoryInfo.path();

        List<RecordingSession> sessions = new ArrayList<>();

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(repositoryPath)) {
            for (Path sessionPath : directoryStream) {
                if (Files.isDirectory(sessionPath)) {
                    String sessionId = repositoryPath.relativize(sessionPath).toString();
                    List<RecordingSource> sources = new ArrayList<>();
                    boolean allSourcesFinished = true;

                    try (DirectoryStream<Path> sourcesStream = Files.newDirectoryStream(sessionPath)) {
                        for (Path sourcePath : sourcesStream) {
                            if (Files.isRegularFile(sourcePath)) {
                                String sourceId = repositoryPath.relativize(sourcePath).toString();
                                String sourceName = sessionPath.relativize(sourcePath).toString();
                                Instant createdAt = Files.getLastModifiedTime(sourcePath).toInstant();
                                Instant lastModifiedAt = createdAt;
                                long size = Files.size(sourcePath);

                                boolean isFileOpen = isFileOpenedByAnotherProcess(sourcePath);
                                RecordingStatus status = isFileOpen
                                        ? RecordingStatus.IN_PROGRESS : RecordingStatus.FINISHED;

                                if (status == RecordingStatus.IN_PROGRESS) {
                                    allSourcesFinished = false;
                                }

                                RecordingSource source = new RecordingSource(
                                        sourceId,
                                        sourceName,
                                        createdAt,
                                        lastModifiedAt,
                                        isFileOpen ? null : lastModifiedAt,
                                        size,
                                        status);

                                sources.add(source);
                            }
                        }
                    } catch (IOException e) {
                        LOG.error("Failed to read sources in session directory: {}", sessionPath, e);
                    }

                    Instant sessionCreatedAt = Files.getLastModifiedTime(sessionPath).toInstant();
                    Instant sessionLastModifiedAt = sessionCreatedAt;

                    RecordingStatus sessionStatus = allSourcesFinished ? RecordingStatus.FINISHED : RecordingStatus.IN_PROGRESS;

                    RecordingSession session = new RecordingSession(
                            sessionId,
                            sessionCreatedAt,
                            sessionLastModifiedAt,
                            allSourcesFinished ? sessionLastModifiedAt : null,
                            sessionStatus,
                            sources);

                    sessions.add(session);
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to read repository directory: {}", repositoryPath, e);
        }

        return sessions;
    }

    private boolean isFileOpenedByAnotherProcess(Path path) {
        try {
            FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE);
            channel.close();
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    @Override
    public void deleteSession(String sessionId) {
        List<DBRepositoryInfo> repositoryInfo = projectRepositoryRepository.getAll();
        if (repositoryInfo.isEmpty()) {
            LOG.warn("No repositories linked to project: project_id={}", projectInfo.id());
            return;
        }

        DBRepositoryInfo repoInfo = repositoryInfo.getFirst();
        Path repositoryPath = repoInfo.path();
        Path sessionPath = repositoryPath.resolve(sessionId);

        if (!Files.isDirectory(sessionPath)) {
            LOG.warn("Session directory does not exist: {}", sessionPath);
            return;
        }

        try {
            // Delete all files in the session directory first
            try (DirectoryStream<Path> sourcesStream = Files.newDirectoryStream(sessionPath)) {
                for (Path sourcePath : sourcesStream) {
                    if (Files.isRegularFile(sourcePath)) {
                        try {
                            Files.delete(sourcePath);
                            LOG.info("Deleted recording file: {}", sourcePath);
                        } catch (IOException e) {
                            LOG.error("Failed to delete recording file: {}", sourcePath, e);
                        }
                    }
                }
            }

            // Then delete the session directory
            Files.delete(sessionPath);
            LOG.info("Deleted session directory: {}", sessionPath);
        } catch (IOException e) {
            LOG.error("Failed to delete session directory: {}", sessionPath, e);
        }
    }
}
