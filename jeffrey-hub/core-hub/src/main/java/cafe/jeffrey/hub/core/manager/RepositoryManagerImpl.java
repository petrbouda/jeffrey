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

package cafe.jeffrey.hub.core.manager;

import tools.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.project.repository.InstanceEnvironmentParser;
import cafe.jeffrey.hub.core.project.repository.MergedRecording;
import cafe.jeffrey.hub.core.jfr.JfrMessageEmitter;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.shared.common.model.repository.InstanceStats;
import cafe.jeffrey.shared.common.model.repository.RepositoryStatistics;
import cafe.jeffrey.shared.common.model.repository.RepositoryStatistics.FileTypeStats;
import cafe.jeffrey.shared.common.model.repository.RepositoryStatistics.StatsCategory;
import cafe.jeffrey.shared.common.model.repository.StreamedRecordingFile;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.shared.common.model.RepositoryInfo;
import cafe.jeffrey.shared.common.model.repository.FileCategory;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.measure.Elapsed;
import cafe.jeffrey.shared.common.measure.Measuring;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.file.Path;
import java.time.Clock;
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
    private final ProjectRepositoryRepository repository;
    private final ProjectInstanceRepository instanceRepository;
    private final RepositoryStorage repositoryStorage;
    private final InstanceEnvironmentParser environmentParser;
    private final TransactionOperations transactionOperations;

    public RepositoryManagerImpl(
            Clock clock,
            ProjectInfo projectInfo,
            ProjectRepositoryRepository repository,
            ProjectInstanceRepository instanceRepository,
            RepositoryStorage repositoryStorage,
            InstanceEnvironmentParser environmentParser,
            TransactionOperations transactionOperations) {

        this.clock = clock;
        this.projectInfo = projectInfo;
        this.repository = repository;
        this.instanceRepository = instanceRepository;
        this.repositoryStorage = repositoryStorage;
        this.environmentParser = environmentParser;
        this.transactionOperations = transactionOperations;
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
        Elapsed<MergedRecording> merged = Measuring.s(() -> repositoryStorage.mergeRecordings(sessionId, recordingFileIds));
        LOG.debug("Merging and streaming recordings completed: sessionId={} durationMs={}",
                sessionId, merged.duration().toMillis());
        return new StreamedRecordingFile(merged.entity().filename(), merged.entity().path(), merged.entity()::close);
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
    public Optional<ObjectNode> sessionEnvironment(String sessionId, boolean expectShutdown) {
        return repositoryStorage.latestFinishedRecordingForSession(sessionId)
                .map(path -> environmentParser.parse(path, expectShutdown));
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

    /**
     * Deletes a recording session directly: the database row, the instance expiring/expired
     * transitions and the on-disk session directory — all inside one transaction. The storage
     * removal runs LAST and still inside the transaction: file deletion cannot be rolled back,
     * so every database write must have succeeded before the irreversible side effect happens;
     * a storage failure rolls the row back and the next retention tick retries the whole
     * deletion.
     */
    @Override
    public void deleteRecordingSession(String recordingSessionId) {
        LOG.debug("Deleting recording session: sessionId={}", recordingSessionId);

        Instant now = clock.instant();
        boolean deleted = Boolean.TRUE.equals(transactionOperations.execute(_ -> {
            Optional<ProjectInstanceSessionInfo> sessionOpt = repository.findSessionById(recordingSessionId);
            if (sessionOpt.isEmpty()) {
                LOG.warn("Recording session not found, nothing to delete: sessionId={} projectId={}",
                        recordingSessionId, projectInfo.id());
                return false;
            }
            String instanceId = sessionOpt.get().instanceId();

            repository.deleteSession(recordingSessionId);
            transitionInstanceAfterSessionDelete(instanceId, now);

            repositoryStorage.deleteSession(recordingSessionId);
            return true;
        }));

        if (deleted) {
            LOG.info("Deleted recording session: sessionId={} projectId={}", recordingSessionId, projectInfo.id());
            JfrMessageEmitter.sessionDeleted(recordingSessionId, projectInfo.id());
        }
    }

    /**
     * Marks the instance as expiring on its first session deletion, and EXPIRED once its last
     * session is gone — the single place this transition happens for session deletions.
     */
    private void transitionInstanceAfterSessionDelete(String instanceId, Instant now) {
        Optional<ProjectInstanceInfo> instanceOpt = instanceRepository.find(instanceId);
        if (instanceOpt.isEmpty()) {
            return;
        }
        ProjectInstanceInfo instance = instanceOpt.get();

        if (instance.expiringAt() == null) {
            instanceRepository.setExpiringAt(instanceId, now);
        }

        List<ProjectInstanceSessionInfo> remainingSessions = instanceRepository.findSessions(instanceId);
        if (remainingSessions.isEmpty() && instance.status() == ProjectInstanceStatus.FINISHED) {
            instanceRepository.updateStatusAndExpiredAt(instanceId, ProjectInstanceStatus.EXPIRED, now);
            LOG.info("Instance marked as EXPIRED (last session deleted): instanceId={} projectId={}",
                    instanceId, projectInfo.id());
        }
    }

    @Override
    public void deleteFilesInSession(String recordingSessionId, List<String> fileIds) {
        repositoryStorage.deleteRepositoryFiles(recordingSessionId, fileIds);
    }

    @Override
    public void setSessionRetained(String recordingSessionId, boolean retained) {
        repository.setSessionRetained(recordingSessionId, retained);
        LOG.info("Updated session retention: sessionId={} projectId={} retained={}",
                recordingSessionId, projectInfo.id(), retained);
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
