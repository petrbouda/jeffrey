/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.model.GraphVisualization;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.configuration.properties.ProjectProperties;
import pbouda.jeffrey.provider.api.repository.ProjectsRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.provider.api.repository.model.CreateProject;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProjectsManagerImpl implements ProjectsManager {

    private final ProjectProperties projectProperties;
    private final Repositories repositories;
    private final ProjectsRepository projectsRepository;
    private final ProjectManager.Factory projectManagerFactory;

    public ProjectsManagerImpl(
            ProjectProperties projectProperties,
            Repositories repositories,
            ProjectsRepository projectsRepository,
            ProjectManager.Factory projectManagerFactory) {

        this.projectProperties = projectProperties;
        this.repositories = repositories;
        this.projectsRepository = projectsRepository;
        this.projectManagerFactory = projectManagerFactory;
    }

    @Override
    public ProjectManager create(ProjectInfo projectInfo) {
        Map<String, String> params = projectProperties.getParams();
        var graphVisualization = new GraphVisualization(
                Config.parseDouble(params, "graph-visualization.flamegraph-min-width", 0.00));

        CreateProject createProject = new CreateProject(projectInfo, graphVisualization);
        ProjectInfo newProjectInfo = projectsRepository.create(createProject);
        ProjectManager projectManager = projectManagerFactory.apply(newProjectInfo);
        projectManager.initialize();
        return projectManager;
    }

    @Override
    public List<? extends ProjectManager> allProjects() {
        return projectsRepository.findAllProjects().stream()
                .map(projectManagerFactory)
                .toList();
    }

    @Override
    public Optional<ProjectManager> project(String projectId) {
        return repositories.newProjectRepository(projectId).find()
                .map(projectManagerFactory);
    }
}
