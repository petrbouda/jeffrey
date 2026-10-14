/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.core.manager;

import cafe.jeffrey.hub.core.project.repository.FileLookup;
import cafe.jeffrey.hub.core.project.repository.SessionDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.jfr.JfrNotificationEmitter;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.hub.model.RepositoryInfo;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RecordingSessionFilter;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class HubRepositoryManager implements RepositoryManager {

    private static final Logger LOG = LoggerFactory.getLogger(HubRepositoryManager.class);

    private final Clock clock;
    private final ProjectInfo projectInfo;
    private final ProjectRepositoryRepository repository;
    private final ProjectInstanceRepository instanceRepository;
    private final RepositoryStorage repositoryStorage;
    private final TransactionOperations transactionOperations;

    public HubRepositoryManager(
            Clock clock,
            ProjectInfo projectInfo,
            ProjectRepositoryRepository repository,
            ProjectInstanceRepository instanceRepository,
            RepositoryStorage repositoryStorage,
            TransactionOperations transactionOperations) {

        this.clock = clock;
        this.projectInfo = projectInfo;
        this.repository = repository;
        this.instanceRepository = instanceRepository;
        this.repositoryStorage = repositoryStorage;
        this.transactionOperations = transactionOperations;
    }

    @Override
    public Optional<RecordingSession> findRecordingSessions(String recordingSessionId) {
        return repositoryStorage.singleSession(recordingSessionId, SessionDetail.WITH_FILES);
    }

    @Override
    public List<RecordingSession> listRecordingSessions(SessionDetail detail, RecordingSessionFilter filter) {
        // Filter on headers first, so only the surviving sessions' directories are walked
        List<RecordingSession> sessions = filter.apply(repositoryStorage.listSessions(SessionDetail.HEADERS));
        return detail.withFiles() ? withFiles(sessions) : sessions;
    }

    @Override
    public List<RecordingSession> listInstanceSessions(String instanceId, RecordingSessionFilter filter) {
        return withFiles(filter.apply(repositoryStorage.listSessionsByInstanceId(instanceId, SessionDetail.HEADERS)));
    }

    private List<RecordingSession> withFiles(List<RecordingSession> sessions) {
        return sessions.stream()
                .map(repositoryStorage::withFiles)
                .toList();
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
    public boolean deleteRecordingSession(String recordingSessionId) {
        LOG.debug("Deleting recording session: session_id={}", recordingSessionId);

        Instant now = clock.instant();
        boolean deleted = Boolean.TRUE.equals(transactionOperations.execute(_ -> {
            Optional<ProjectInstanceSessionInfo> sessionOpt = repository.findSessionById(recordingSessionId);
            if (sessionOpt.isEmpty()) {
                LOG.warn("Recording session not found, nothing to delete: session_id={} project_id={}",
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
            LOG.info("Deleted recording session: session_id={} project_id={}", recordingSessionId, projectInfo.id());
            JfrNotificationEmitter.sessionDeleted(recordingSessionId, projectInfo.id());
        }
        return deleted;
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
            LOG.info("Instance marked as EXPIRED (last session deleted): instance_id={} project_id={}",
                    instanceId, projectInfo.id());
        }
    }

    @Override
    public void setSessionRetained(String recordingSessionId, boolean retained) {
        repository.setSessionRetained(recordingSessionId, retained);
        LOG.info("Updated session retention: session_id={} project_id={} retained={}",
                recordingSessionId, projectInfo.id(), retained);
    }

    @Override
    public FileLookup file(String sessionId, String fileId) {
        return repositoryStorage.file(sessionId, fileId);
    }

    @Override
    public void delete() {
        LOG.debug("Deleting repository");
        repository.deleteAll();
    }
}
