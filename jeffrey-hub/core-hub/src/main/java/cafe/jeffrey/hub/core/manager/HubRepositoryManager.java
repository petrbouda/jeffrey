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

package cafe.jeffrey.hub.core.manager;

import cafe.jeffrey.hub.core.jfr.JfrNotificationEmitter;
import cafe.jeffrey.hub.core.project.repository.FileLookup;
import cafe.jeffrey.hub.core.project.repository.InstanceSessions;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.project.repository.SessionDetail;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RecordingSessionFilter;
import cafe.jeffrey.shared.volume.layout.SessionDeletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class HubRepositoryManager implements RepositoryManager {

    private static final Logger LOG = LoggerFactory.getLogger(HubRepositoryManager.class);

    private final ProjectInfo projectInfo;
    private final RepositoryStorage repositoryStorage;

    public HubRepositoryManager(ProjectInfo projectInfo, RepositoryStorage repositoryStorage) {
        this.projectInfo = projectInfo;
        this.repositoryStorage = repositoryStorage;
    }

    @Override
    public Optional<RecordingSession> findSession(String sessionId) {
        return repositoryStorage.session(sessionId);
    }

    @Override
    public List<RecordingSession> listRecordingSessions(RecordingSessionFilter filter) {
        // Filter on headers first, so only the surviving sessions' directories are walked
        return withFiles(filter.apply(repositoryStorage.listSessions(SessionDetail.HEADERS)));
    }

    @Override
    public List<ProjectInstanceInfo> instances() {
        return repositoryStorage.instances();
    }

    @Override
    public Optional<InstanceSessions> instance(String instanceId) {
        return repositoryStorage.instanceWithSessions(instanceId)
                .map(found -> new InstanceSessions(found.instance(), withFiles(found.sessions())));
    }

    private List<RecordingSession> withFiles(List<RecordingSession> sessions) {
        return sessions.stream()
                .map(repositoryStorage::withFiles)
                .toList();
    }

    @Override
    public SessionDeletion deleteRecordingSession(String recordingSessionId) {
        SessionDeletion deletion = repositoryStorage.deleteSession(recordingSessionId);
        if (!deletion.deleted()) {
            LOG.warn("Recording session not found, nothing to delete: session_id={} project_id={}",
                    recordingSessionId, projectInfo.id());
            return deletion;
        }

        LOG.info("Deleted recording session: session_id={} project_id={} instance_deleted={}",
                recordingSessionId, projectInfo.id(), deletion.instanceDeleted());
        JfrNotificationEmitter.sessionDeleted(recordingSessionId, projectInfo.id());
        return deletion;
    }

    @Override
    public boolean setSessionRetained(String recordingSessionId, boolean retained) {
        return repositoryStorage.setRetained(recordingSessionId, retained);
    }

    @Override
    public FileLookup file(String sessionId, String fileId) {
        return repositoryStorage.file(sessionId, fileId);
    }
}
