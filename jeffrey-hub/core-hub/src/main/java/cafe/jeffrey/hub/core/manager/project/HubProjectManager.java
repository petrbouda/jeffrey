/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

    @Override
    public void restore() {
        projectRepository.restore();
        LOG.info("Restored project: project_id={}", projectInfo.id());
    }

    /**
     * Deletes the project directly: the SQL cascade commits first, then the project's
     * directory tree is removed from the workspace volume. The directory removal is what
     * makes the deletion final — the workspace reconciler re-creates any project whose
     * on-disk declaration still exists. It runs after the commit and best-effort: leftover
     * files are cheaper than a permanently stuck project delete.
     */
    @Override
    public void delete() {
        LOG.debug("Deleting project: project_id={}", info().id());

        transactionOperations.executeWithoutResult(_ -> {
            // SQL cascade deletes all project metadata (instances, sessions, schedulers, etc.)
            projectRepository.delete();
        });

        try {
            repositoryStorage.deleteProjectDirectory();
        } catch (Exception e) {
            LOG.warn("Failed to delete project directory: project_id={}", projectInfo.id(), e);
        }

        LOG.info("Deleted project: project_id={}", projectInfo.id());
        JfrNotificationEmitter.projectDeleted(projectInfo.id());
    }
}
