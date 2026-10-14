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

package cafe.jeffrey.hub.core.manager.project;

import cafe.jeffrey.hub.core.project.repository.SessionDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;
import cafe.jeffrey.hub.core.jfr.JfrNotificationEmitter;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.hub.persistence.api.ProjectRepository;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RecordingStatus;

import java.util.List;

/**
 * Hub-specific project manager — no profile analysis, recording management,
 * or download capabilities. The hub is a pure collector.
 */
public class HubProjectManager implements ProjectManager {

    private static final Logger LOG = LoggerFactory.getLogger(HubProjectManager.class);

    private final ProjectInfo projectInfo;
    private final ProjectRepository projectRepository;
    private final HubPlatformRepositories platformRepositories;
    private final RepositoryStorage repositoryStorage;
    private final RepositoryManager.Factory repositoryManagerFactory;
    private final TransactionOperations transactionOperations;

    public HubProjectManager(
            ProjectInfo projectInfo,
            HubPlatformRepositories platformRepositories,
            RepositoryStorage repositoryStorage,
            RepositoryManager.Factory repositoryManagerFactory,
            TransactionOperations transactionOperations) {

        this.projectInfo = projectInfo;
        this.projectRepository = platformRepositories.newProjectRepository(projectInfo.id());
        this.platformRepositories = platformRepositories;
        this.repositoryStorage = repositoryStorage;
        this.repositoryManagerFactory = repositoryManagerFactory;
        this.transactionOperations = transactionOperations;
    }

    @Override
    public RepositoryStorage repositoryStorage() {
        return repositoryStorage;
    }

    @Override
    public RepositoryManager repositoryManager() {
        return repositoryManagerFactory.apply(projectInfo);
    }

    @Override
    public ProjectInstanceRepository projectInstanceRepository() {
        return platformRepositories.newProjectInstanceRepository(projectInfo.id());
    }

    @Override
    public ProjectInfo info() {
        return projectInfo;
    }

    @Override
    public DetailedProjectInfo detailedInfo() {
        List<RecordingSession> recordingSessions = repositoryManager()
                .listRecordingSessions(SessionDetail.HEADERS);

        RecordingStatus recordingStatus = recordingSessions.isEmpty()
                ? null
                : recordingSessions.stream().anyMatch(session -> session.status() == RecordingStatus.ACTIVE)
                        ? RecordingStatus.ACTIVE
                        : RecordingStatus.FINISHED;

        return new DetailedProjectInfo(projectInfo, recordingStatus, recordingSessions.size());
    }

    /**
     * Deletes the project for good: its directory tree on the workspace volume, then its rows.
     *
     * <p>The directory goes first because its location is read from the project's repository
     * row, which the cascade removes. Its removal is also what makes the deletion final — the
     * workspace reconciler re-creates any project whose on-disk declaration still exists. It is
     * best-effort: leftover files are cheaper than a permanently stuck project delete.
     */
    @Override
    public void delete() {
        LOG.debug("Deleting project: project_id={}", info().id());

        try {
            repositoryStorage.deleteProjectDirectory();
        } catch (Exception e) {
            LOG.warn("Failed to delete project directory: project_id={}", projectInfo.id(), e);
        }

        transactionOperations.executeWithoutResult(_ -> {
            // SQL cascade deletes all project metadata (instances, sessions, repository, project row)
            projectRepository.delete();
        });

        LOG.info("Deleted project: project_id={}", projectInfo.id());
        JfrNotificationEmitter.projectDeleted(projectInfo.id());
    }
}
