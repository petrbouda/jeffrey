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

import pbouda.jeffrey.common.model.ExternalProjectLink;
import pbouda.jeffrey.common.pipeline.Pipeline;
import pbouda.jeffrey.project.ProjectTemplate;
import pbouda.jeffrey.project.ProjectTemplatesLoader;
import pbouda.jeffrey.project.TemplateTarget;
import pbouda.jeffrey.project.pipeline.CreateProjectContext;
import pbouda.jeffrey.provider.api.repository.ProjectsRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;

import java.util.List;
import java.util.Optional;

public class ProjectsManagerImpl implements ProjectsManager {

    private final Pipeline<CreateProjectContext> createProjectPipeline;
    private final Repositories repositories;
    private final ProjectsRepository projectsRepository;
    private final ProjectManager.Factory projectManagerFactory;
    private final ProjectTemplatesLoader projectTemplatesLoader;

    public ProjectsManagerImpl(
            Pipeline<CreateProjectContext> createProjectPipeline,
            Repositories repositories,
            ProjectsRepository projectsRepository,
            ProjectManager.Factory projectManagerFactory,
            ProjectTemplatesLoader projectTemplatesLoader) {

        this.createProjectPipeline = createProjectPipeline;
        this.repositories = repositories;
        this.projectsRepository = projectsRepository;
        this.projectManagerFactory = projectManagerFactory;
        this.projectTemplatesLoader = projectTemplatesLoader;
    }

    @Override
    public ProjectManager create(String name, String templateId, ExternalProjectLink externalProjectLink) {
        CreateProjectContext context = new CreateProjectContext(name, templateId, externalProjectLink);
        context = createProjectPipeline.execute(context);

        ProjectManager projectManager = projectManagerFactory.apply(context.projectInfo());
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

    @Override
    public List<ProjectTemplate> templates(TemplateTarget templateTarget) {
        List<ProjectTemplate> projectTemplates = projectTemplatesLoader.loadAll();
        if (templateTarget == null) {
            return projectTemplates;
        }

        return projectTemplates.stream()
                .filter(template -> template.target() == null || template.target().equals(templateTarget))
                .toList();
    }
}
