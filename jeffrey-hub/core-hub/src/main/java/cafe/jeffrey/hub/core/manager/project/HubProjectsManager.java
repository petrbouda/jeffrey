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
