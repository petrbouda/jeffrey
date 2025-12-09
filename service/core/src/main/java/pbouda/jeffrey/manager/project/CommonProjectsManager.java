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

package pbouda.jeffrey.manager.project;

import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.pipeline.Pipeline;
import pbouda.jeffrey.manager.model.CreateProject;
import pbouda.jeffrey.project.pipeline.CreateProjectContext;
import pbouda.jeffrey.provider.api.repository.ProjectsRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;

import java.util.List;
import java.util.Optional;

public class CommonProjectsManager implements ProjectsManager {

    private final WorkspaceInfo workspaceInfo;
    private final Pipeline<CreateProjectContext> createProjectPipeline;
    private final Repositories repositories;
    private final ProjectsRepository projectsRepository;
    private final ProjectManager.Factory projectManagerFactory;

    public CommonProjectsManager(
            WorkspaceInfo workspaceInfo,
            Pipeline<CreateProjectContext> createProjectPipeline,
            Repositories repositories,
            ProjectsRepository projectsRepository,
            ProjectManager.Factory projectManagerFactory) {

        this.workspaceInfo = workspaceInfo;
        this.createProjectPipeline = createProjectPipeline;
        this.repositories = repositories;
        this.projectsRepository = projectsRepository;
        this.projectManagerFactory = projectManagerFactory;
    }

    @Override
    public ProjectManager create(CreateProject createProject) {
        CreateProjectContext context = new CreateProjectContext(createProject);
        context = createProjectPipeline.execute(context);

        ProjectManager projectManager = projectManagerFactory.apply(context.projectInfo());
        projectManager.initialize();
        return projectManager;
    }

    @Override
    public List<ProjectManager> findAll() {
         return projectsRepository.findAllProjects(workspaceInfo.id()).stream()
                .map(projectManagerFactory)
                .toList();
    }

    @Override
    public Optional<ProjectManager> project(String projectId) {
        return repositories.newProjectRepository(projectId).find()
                .map(projectManagerFactory);
    }

    @Override
    public Optional<ProjectManager> findByOriginProjectId(String originProjectId) {
        return projectsRepository.findByOriginProjectId(originProjectId)
                .map(projectManagerFactory);
    }
}
