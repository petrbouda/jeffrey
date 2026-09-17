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

import cafe.jeffrey.hub.core.project.repository.SessionDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.jfr.JfrNotificationEmitter;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.model.repository.RepositoryStatistics;
import cafe.jeffrey.hub.model.repository.StreamedFile;
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

import java.nio.file.Path;
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
        // The filter reads headers only, so files are walked for the sessions that survive it —
        // a limit of five over a project of hundreds lists five directories, not hundreds
        List<RecordingSession> sessions = filter.apply(repositoryStorage.listSessions(SessionDetail.HEADERS));
        if (!detail.withFiles()) {
            return sessions;
        }
        return sessions.stream()
                .map(repositoryStorage::withFiles)
                .toList();
    }

    @Override
    public RepositoryStatistics calculateRepositoryStatistics() {
        long totalSize = listRecordingSessions(SessionDetail.WITH_FILES).stream()
                .mapToLong(RecordingSession::totalSizeBytes)
                .sum();

        return new RepositoryStatistics(totalSize);
    }

    @Override
    public List<RecordingSession> instanceSessions(String instanceId) {
        return repositoryStorage.listSessionsByInstanceId(instanceId, SessionDetail.WITH_FILES);
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

    /**
     * Deletes the named files of a session, refusing the one file of it that is not finished.
     *
     * <p>The guard sits here rather than in the storage because this is where ids arrive from
     * outside — the UI's delete, over gRPC. The retention jobs reach the storage directly with
     * ids they took from {@link RecordingSession#finishedRecordings()}, so they can never name
     * the open chunk and should not pay for a second listing of the session to be told so.
     *
     * <p>Deleting it would take the file the profiler is writing into out from under it: the
     * recording loses the chunk in flight, and the profiler writes on to a path that no longer
     * has a directory entry.
     */
    @Override
    public void deleteFilesInSession(String recordingSessionId, List<String> fileIds) {
        RecordingSession session = repositoryStorage.singleSession(recordingSessionId, SessionDetail.WITH_FILES)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + recordingSessionId));

        session.openRecording()
                .filter(open -> fileIds.contains(open.id()))
                .ifPresent(open -> {
                    throw new IllegalArgumentException("File " + open.name() + " is the chunk the profiler "
                            + "is still writing for session " + recordingSessionId + ", and deleting it would "
                            + "take it out from under the profiler. It can be deleted once the profiler has "
                            + "rolled the next one.");
                });

        repositoryStorage.deleteRepositoryFiles(recordingSessionId, fileIds);
    }

    @Override
    public void setSessionRetained(String recordingSessionId, boolean retained) {
        repository.setSessionRetained(recordingSessionId, retained);
        LOG.info("Updated session retention: session_id={} project_id={} retained={}",
                recordingSessionId, projectInfo.id(), retained);
    }

    /**
     * One file of a session, whatever kind it is.
     *
     * <p>There were two of these, one per category, identical but for the word in their refusal.
     * A category says what a reader does with a file, not whether the hub will hand it over, and
     * the reader knows the category already — it arrives with every listing.
     */
    @Override
    public StreamedFile streamFile(String sessionId, String fileId) {
        Path filePath = repositoryStorage.file(sessionId, fileId);
        return new StreamedFile(filePath.getFileName().toString(), filePath);
    }

    @Override
    public void delete() {
        LOG.debug("Deleting repository");
        repository.deleteAll();
    }
}
