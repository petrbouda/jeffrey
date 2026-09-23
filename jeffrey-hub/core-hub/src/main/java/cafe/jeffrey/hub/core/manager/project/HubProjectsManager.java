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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.model.CreateProject;
import cafe.jeffrey.hub.persistence.api.ProjectsRepository;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;

import java.util.List;
import java.util.Optional;

public class HubProjectsManager implements ProjectsManager {

    private static final Logger LOG = LoggerFactory.getLogger(HubProjectsManager.class);

    private final WorkspaceInfo workspaceInfo;
    private final ProjectCreator projectCreator;
    private final HubPlatformRepositories platformRepositories;
    private final ProjectsRepository projectsRepository;
    private final ProjectManager.Factory projectManagerFactory;

    public HubProjectsManager(
            WorkspaceInfo workspaceInfo,
            ProjectCreator projectCreator,
            HubPlatformRepositories platformRepositories,
            ProjectManager.Factory projectManagerFactory) {

        this.workspaceInfo = workspaceInfo;
        this.projectCreator = projectCreator;
        this.platformRepositories = platformRepositories;
        this.projectsRepository = platformRepositories.newProjectsRepository();
        this.projectManagerFactory = projectManagerFactory;
    }

    @Override
    public ProjectManager create(CreateProject createProject) {
        LOG.debug("Creating project: project_name={}", createProject.projectName());
        return projectManagerFactory.apply(projectCreator.create(createProject));
    }

    @Override
    public List<ProjectManager> findAll() {
         return projectsRepository.findAllProjects(workspaceInfo.id()).stream()
                .map(projectManagerFactory)
                .toList();
    }

    @Override
    public List<ProjectManager> findAllIncludingDeleted() {
        return projectsRepository.findAllProjectsIncludingDeleted(workspaceInfo.id()).stream()
                .map(projectManagerFactory)
                .toList();
    }

    @Override
    public Optional<ProjectManager> project(String projectId) {
        return platformRepositories.newProjectRepository(projectId).find()
                .filter(project -> workspaceInfo.id().equals(project.workspaceId()))
                .map(projectManagerFactory);
    }

    @Override
    public Optional<ProjectManager> findByOriginProjectId(String originProjectId) {
        return projectsRepository.findByOriginProjectId(originProjectId)
                .map(projectManagerFactory);
    }

}
