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
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.shared.volume.layout.SessionDeletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HubRepositoryManager implements RepositoryManager {

    private static final Logger LOG = LoggerFactory.getLogger(HubRepositoryManager.class);

    private final ProjectInfo projectInfo;
    private final RepositoryStorage repositoryStorage;

    public HubRepositoryManager(ProjectInfo projectInfo, RepositoryStorage repositoryStorage) {
        this.projectInfo = projectInfo;
        this.repositoryStorage = repositoryStorage;
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
}
