/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.server.core.manager.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.server.core.project.pipeline.Pipeline;
import cafe.jeffrey.shared.common.model.CreateProject;
import cafe.jeffrey.server.core.project.pipeline.CreateProjectContext;
import cafe.jeffrey.server.persistence.api.ProjectsRepository;
import cafe.jeffrey.server.persistence.api.ServerPlatformRepositories;

import java.util.List;
import java.util.Optional;

public class LiveProjectsManager implements ProjectsManager {

    private static final Logger LOG = LoggerFactory.getLogger(LiveProjectsManager.class);

    private final WorkspaceInfo workspaceInfo;
    private final Pipeline<CreateProjectContext> createProjectPipeline;
    private final ServerPlatformRepositories platformRepositories;
    private final ProjectsRepository projectsRepository;
    private final ProjectManager.Factory projectManagerFactory;

    public LiveProjectsManager(
            WorkspaceInfo workspaceInfo,
            Pipeline<CreateProjectContext> createProjectPipeline,
            ServerPlatformRepositories platformRepositories,
            ProjectManager.Factory projectManagerFactory) {

        this.workspaceInfo = workspaceInfo;
        this.createProjectPipeline = createProjectPipeline;
        this.platformRepositories = platformRepositories;
        this.projectsRepository = platformRepositories.newProjectsRepository();
        this.projectManagerFactory = projectManagerFactory;
    }

    @Override
    public ProjectManager create(CreateProject createProject) {
        LOG.debug("Creating project: name={}", createProject.projectName());
        CreateProjectContext context = new CreateProjectContext(createProject);
        context = createProjectPipeline.execute(context);
        return projectManagerFactory.apply(context.projectInfo());
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
                .map(projectManagerFactory);
    }

    @Override
    public Optional<ProjectManager> findByOriginProjectId(String originProjectId) {
        return projectsRepository.findByOriginProjectId(originProjectId)
                .map(projectManagerFactory);
    }

    @Override
    public List<String> findAllNamespaces() {
        return projectsRepository.findAllNamespaces();
    }
}
