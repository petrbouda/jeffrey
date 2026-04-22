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

package pbouda.jeffrey.server.core.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.server.core.project.repository.MergedRecording;
import pbouda.jeffrey.server.core.project.repository.RepositoryStorage;
import pbouda.jeffrey.server.core.scheduler.SchedulerTrigger;
import pbouda.jeffrey.server.core.workspace.WorkspaceEventConverter;
import pbouda.jeffrey.server.core.workspace.WorkspaceEventPublisher;
import pbouda.jeffrey.shared.common.model.repository.InstanceStats;
import pbouda.jeffrey.shared.common.model.repository.RepositoryStatistics;
import pbouda.jeffrey.shared.common.model.repository.RepositoryStatistics.FileTypeStats;
import pbouda.jeffrey.shared.common.model.repository.RepositoryStatistics.StatsCategory;
import pbouda.jeffrey.shared.common.model.repository.StreamedRecordingFile;
import pbouda.jeffrey.server.persistence.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.repository.FileCategory;
import pbouda.jeffrey.shared.common.model.repository.RecordingSession;
import pbouda.jeffrey.shared.common.model.repository.RecordingStatus;
import pbouda.jeffrey.shared.common.model.repository.RepositoryFile;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class RepositoryManagerImpl implements RepositoryManager {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryManagerImpl.class);

    private final Clock clock;
    private final ProjectInfo projectInfo;
    private final SchedulerTrigger projectsSynchronizerTrigger;
    private final ProjectRepositoryRepository repository;
    private final RepositoryStorage repositoryStorage;
    private final WorkspaceEventPublisher workspaceEventPublisher;

    public RepositoryManagerImpl(
            Clock clock,
            ProjectInfo projectInfo,
            SchedulerTrigger projectsSynchronizerTrigger,
            ProjectRepositoryRepository repository,
            RepositoryStorage repositoryStorage,
            WorkspaceEventPublisher workspaceEventPublisher) {

        this.clock = clock;
        this.projectInfo = projectInfo;
        this.projectsSynchronizerTrigger = projectsSynchronizerTrigger;
        this.repository = repository;
        this.repositoryStorage = repositoryStorage;
        this.workspaceEventPublisher = workspaceEventPublisher;
    }

    @Override
    public StreamedRecordingFile streamArtifactFile(String sessionId, String fileId) {
        RepositoryFile file = findAndValidateFile(sessionId, fileId);

        if (!file.isArtifactFile()) {
            throw new IllegalArgumentException("File is not an artifact: fileId=" + fileId);
        }

        List<Path> paths = repositoryStorage.artifacts(sessionId, List.of(fileId));
        if (paths.isEmpty()) {
            throw new IllegalArgumentException("Artifact file path not found: fileId=" + fileId);
        }

        Path filePath = paths.getFirst();
        return new StreamedRecordingFile(filePath.getFileName().toString(), filePath);
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
        return repositoryStorage.listSessions(withFiles);
    }

    @Override
    public RepositoryStatistics calculateRepositoryStatistics() {
        List<RecordingSession> sessions = this.listRecordingSessions(true);
        if (sessions.isEmpty()) {
            return RepositoryStatistics.EMPTY;
        }

        List<RepositoryFile> allFiles = sessions.stream()
                .flatMap(s -> s.files().stream())
                .toList();

        Map<StatsCategory, FileTypeStats> byCategory = allFiles.stream()
                .collect(Collectors.groupingBy(
                        f -> StatsCategory.of(f.fileType()),
                        Collectors.teeing(
                                Collectors.counting(),
                                Collectors.summingLong(f -> fileSize(f)),
                                (count, size) -> new FileTypeStats(count.intValue(), size))));

        long totalSize = allFiles.stream().mapToLong(this::fileSize).sum();

        long lastActivity = allFiles.stream()
                .map(RepositoryFile::createdAt)
                .filter(Objects::nonNull)
                .mapToLong(Instant::toEpochMilli)
                .max()
                .orElse(0L);

        long biggestSession = sessions.stream()
                .mapToLong(s -> s.files().stream().mapToLong(this::fileSize).sum())
                .max()
                .orElse(0L);

        return RepositoryStatistics.fromCategoryMap(
                sessions.size(),
                sessions.getFirst().status(),
                lastActivity,
                totalSize,
                allFiles.size(),
                biggestSession,
                byCategory);
    }

    private long fileSize(RepositoryFile file) {
        return file.size() != null ? file.size() : 0L;
    }

    @Override
    public InstanceStats instanceStats(String instanceId) {
        List<RecordingSession> sessions = repositoryStorage.listSessionsByInstanceId(instanceId, true);
        if (sessions.isEmpty()) {
            return InstanceStats.EMPTY;
        }

        int fileCount = sessions.stream()
                .mapToInt(s -> s.files().size())
                .sum();

        long totalSize = sessions.stream()
                .flatMap(s -> s.files().stream())
                .mapToLong(this::fileSize)
                .sum();

        return new InstanceStats(fileCount, totalSize);
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

        workspaceEventPublisher.publishBatch(projectInfo.workspaceId(), List.of(workspaceEvent));

        // Trigger event synchronization
        projectsSynchronizerTrigger.execute();
    }

    @Override
    public void deleteFilesInSession(String recordingSessionId, List<String> fileIds) {
        repositoryStorage.deleteRepositoryFiles(recordingSessionId, fileIds);
    }

    @Override
    public StreamedRecordingFile streamRecordingFile(String sessionId, String fileId) {
        RepositoryFile file = findAndValidateFile(sessionId, fileId);

        if (!file.isRecordingFile()) {
            throw new IllegalArgumentException("File is not a recording: fileId=" + fileId);
        }

        List<Path> paths = repositoryStorage.recordings(sessionId, List.of(fileId));
        if (paths.isEmpty()) {
            throw new IllegalArgumentException("Recording file path not found: fileId=" + fileId);
        }

        Path filePath = paths.getFirst();
        return new StreamedRecordingFile(filePath.getFileName().toString(), filePath);
    }

    private RepositoryFile findAndValidateFile(String sessionId, String fileId) {
        RecordingSession session = repositoryStorage.singleSession(sessionId, true)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        RepositoryFile file = session.files().stream()
                .filter(f -> f.id().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("File not found: fileId=" + fileId));

        if (file.status() == RecordingStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot download ACTIVE file: fileId=" + fileId);
        }

        if (file.fileType().fileCategory() == FileCategory.TEMPORARY) {
            throw new IllegalArgumentException("Cannot download temporary file: fileId=" + fileId);
        }

        return file;
    }

    @Override
    public void delete() {
        LOG.debug("Deleting repository");
        repository.deleteAll();
    }
}
