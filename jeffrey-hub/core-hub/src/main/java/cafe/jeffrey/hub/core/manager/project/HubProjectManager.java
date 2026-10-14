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

import cafe.jeffrey.hub.core.jfr.JfrNotificationEmitter;
import cafe.jeffrey.hub.core.manager.HubRepositoryManager;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.project.repository.FilesystemRepositoryStorage;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.volume.ProjectDirectory;
import cafe.jeffrey.hub.core.volume.SessionLiveness;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.hub.model.repository.RecordingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class HubProjectManager implements ProjectManager {

    private static final Logger LOG = LoggerFactory.getLogger(HubProjectManager.class);

    private final ProjectInfo projectInfo;
    private final RepositoryStorage repositoryStorage;

    public HubProjectManager(ProjectDirectory project, SessionLiveness liveness) {
        this.projectInfo = project.info();
        this.repositoryStorage = new FilesystemRepositoryStorage(project.info(), project.path(), liveness);
    }

    @Override
    public RepositoryStorage repositoryStorage() {
        return repositoryStorage;
    }

    @Override
    public RepositoryManager repositoryManager() {
        return new HubRepositoryManager(projectInfo, repositoryStorage);
    }

    @Override
    public ProjectInfo info() {
        return projectInfo;
    }

    /**
     * The project's status and session count, read off its instances — each from its newest
     * session alone, so listing projects never reads an older session's liveness files.
     */
    @Override
    public DetailedProjectInfo detailedInfo() {
        List<ProjectInstanceInfo> instances = repositoryStorage.instances();
        int sessionCount = instances.stream().mapToInt(ProjectInstanceInfo::sessionCount).sum();
        RecordingStatus status = sessionCount == 0
                ? null
                : instances.stream().anyMatch(instance -> instance.status() == ProjectInstanceStatus.ACTIVE)
                        ? RecordingStatus.ACTIVE
                        : RecordingStatus.FINISHED;
        return new DetailedProjectInfo(projectInfo, status, sessionCount);
    }

    @Override
    public void delete() {
        repositoryStorage.deleteProjectDirectory();
        LOG.info("Deleted project: project_id={}", projectInfo.id());
        JfrNotificationEmitter.projectDeleted(projectInfo.id());
    }
}
