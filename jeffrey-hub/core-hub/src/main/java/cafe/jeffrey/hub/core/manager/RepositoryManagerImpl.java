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
import cafe.jeffrey.hub.core.jfr.JfrNotificationEmitter;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.repository.InstanceStats;
import cafe.jeffrey.shared.common.model.repository.RecordingChunks;
import cafe.jeffrey.shared.common.model.repository.RepositoryStatistics;
import cafe.jeffrey.shared.common.model.repository.RepositoryStatistics.FileTypeStats;
import cafe.jeffrey.shared.common.model.repository.StatsCategory;
import cafe.jeffrey.shared.common.model.repository.StreamedFile;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.shared.common.model.RepositoryInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingSessionFilter;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import org.springframework.transaction.support.TransactionOperations;

import java.io.IOException;
import java.nio.file.Files;
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
    public StreamedFile streamFile(String sessionId, String fileId) {
        RecordingSession session = repositoryStorage.singleSession(sessionId, true)
                .orElseThrow(() -> Exceptions.recordingSessionNotFound(sessionId));
        RepositoryFile file = findAndValidateFile(session, fileId);

        Path filePath = file.filePath();
        if (filePath == null || !Files.isRegularFile(filePath)) {
            throw Exceptions.resourceNotFound("File is no longer on disk: fileId=" + fileId);
        }
        if (!liesInside(filePath, session.absolutePath())) {
            // The listing already skips links; this is the same rule at the moment of serving,
            // for a path that stopped being what the listing saw.
            throw new IllegalArgumentException("File is not inside its session directory: fileId=" + fileId);
        }

        return new StreamedFile(filePath.getFileName().toString(), filePath);
    }

    /**
     * Whether the file, with every link on its path resolved, is under the session directory
     * resolved the same way. Nothing the hub serves may lie anywhere else, whatever a name in the
     * session directory points at.
     */
    private static boolean liesInside(Path file, Path sessionDir) {
        if (sessionDir == null) {
            return false;
        }
        try {
            return file.toRealPath().startsWith(sessionDir.toRealPath());
        } catch (IOException e) {
            LOG.debug("Cannot resolve a session file against its directory: file={} reason={}", file, e.getMessage());
            return false;
        }
    }

    @Override
    public Optional<RecordingSession> findRecordingSessions(String recordingSessionId) {
        return repositoryStorage.singleSession(recordingSessionId, true);
    }

    @Override
    public List<RecordingSession> listRecordingSessions(boolean withFiles, RecordingSessionFilter filter) {
        return filter.apply(repositoryStorage.listSessions(withFiles));
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
                        f -> f.fileType().statsCategory(),
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
        return repositoryStorage.latestFinishedChunk(sessionId)
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
            JfrNotificationEmitter.sessionDeleted(recordingSessionId, projectInfo.id());
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

    /**
     * The session's file with the given id, checked to be servable. A chunk being compressed is
     * listed twice under one id, raw and compressed; the compressed form is the one that is
     * complete whenever it exists, and the one served — see {@link RecordingChunks#preferred}.
     */
    private static RepositoryFile findAndValidateFile(RecordingSession session, String fileId) {
        RepositoryFile file = session.files().stream()
                .filter(f -> f.id().equals(fileId))
                .reduce(RecordingChunks::preferred)
                .orElseThrow(() -> Exceptions.resourceNotFound("File not found: fileId=" + fileId));

        if (!file.isFinished()) {
            throw new IllegalArgumentException("Cannot download a file that is still being written: fileId=" + fileId);
        }

        if (file.isTransient()) {
            throw new IllegalArgumentException("Cannot download a transient file: fileId=" + fileId);
        }

        return file;
    }

    @Override
    public void delete() {
        LOG.debug("Deleting repository");
        repository.deleteAll();
    }
}
