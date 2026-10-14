/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.hub.core.scheduler.job;

import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.project.repository.SessionDetail;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RepositoryFile;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One project's retention pass: the project's sessions listed once, with their files, and every
 * deletion made through here so the listing keeps saying what is left. A rule applied after
 * another therefore sees what the earlier one freed, and the storage figure measured at the end
 * is what remains on disk.
 */
final class ProjectRetentionPass {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectRetentionPass.class);

    /**
     * What a pass did to one project.
     *
     * @param remaining the sessions left once every rule ran, with their files
     */
    record Outcome(int sessionsDeleted, int filesDeleted, int instancesDeleted, List<RecordingSession> remaining) {
    }

    private final ProjectManager project;
    private final RepositoryStorage storage;
    private final Instant now;
    private final List<RecordingSession> sessions;

    private int sessionsDeleted;
    private int filesDeleted;
    private int instancesDeleted;

    private ProjectRetentionPass(ProjectManager project, Instant now) {
        this.project = project;
        this.storage = project.repositoryStorage();
        this.now = now;
        this.sessions = new ArrayList<>(storage.listSessions(SessionDetail.WITH_FILES));
    }

    /** Lists the project's sessions with their files: the one walk of its directories this pass makes. */
    static ProjectRetentionPass begin(ProjectManager project, Instant now) {
        return new ProjectRetentionPass(project, now);
    }

    String projectName() {
        return project.info().name();
    }

    Instant now() {
        return now;
    }

    /** The sessions still there, as a copy a rule may iterate while it deletes. */
    List<RecordingSession> sessions() {
        return List.copyOf(sessions);
    }

    ProjectInstanceRepository instances() {
        return project.projectInstanceRepository();
    }

    long totalSizeBytes() {
        return sessions.stream()
                .mapToLong(RecordingSession::totalSizeBytes)
                .sum();
    }

    /**
     * Deletes a whole session: its row, its directory and its instance's lifecycle transition.
     *
     * @return whether it was there to delete; one already gone frees nothing
     */
    boolean deleteSession(RecordingSession session) {
        boolean deleted = project.repositoryManager().deleteRecordingSession(session.id());
        if (deleted) {
            sessions.remove(session);
            sessionsDeleted++;
        }
        return deleted;
    }

    /** Deletes some files of a session; the session stays, without them. */
    void deleteFiles(RecordingSession session, List<RepositoryFile> files) {
        if (files.isEmpty()) {
            return;
        }
        List<String> ids = files.stream()
                .map(RepositoryFile::id)
                .toList();
        storage.deleteRepositoryFiles(session.id(), ids);
        filesDeleted += files.size();

        Set<String> deletedIds = Set.copyOf(ids);
        List<RepositoryFile> kept = session.files().stream()
                .filter(file -> !deletedIds.contains(file.id()))
                .toList();
        sessions.set(sessions.indexOf(session), new RecordingSession(
                session.id(), session.name(), session.instanceId(), session.createdAt(), session.finishedAt(),
                session.status(), kept, session.retained(), session.heartbeatMissing()));
    }

    /**
     * Deletes an instance's row and then its directory. Without the directory the reconciler would
     * re-create the instance from disk; a failure there is logged, and the next pass deletes again.
     */
    void deleteInstance(String instanceId) {
        instances().delete(instanceId);
        instancesDeleted++;
        try {
            storage.deleteInstanceDirectory(instanceId);
        } catch (RuntimeException e) {
            LOG.warn("Failed to delete instance directory: instance_id={}", instanceId, e);
        }
    }

    Outcome outcome() {
        return new Outcome(sessionsDeleted, filesDeleted, instancesDeleted, List.copyOf(sessions));
    }
}
