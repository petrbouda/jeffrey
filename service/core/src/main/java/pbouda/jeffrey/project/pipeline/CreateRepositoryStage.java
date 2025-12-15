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

package pbouda.jeffrey.project.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.pipeline.Stage;
import pbouda.jeffrey.manager.model.CreateProject;
import pbouda.jeffrey.project.ProjectRepository;
import pbouda.jeffrey.project.template.ProjectTemplate;
import pbouda.jeffrey.project.template.ProjectTemplatesLoader;
import pbouda.jeffrey.provider.api.model.DBRepositoryInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;

import java.util.Objects;
import java.util.Optional;

public class CreateRepositoryStage implements Stage<CreateProjectContext> {

    private static final Logger LOG = LoggerFactory.getLogger(CreateRepositoryStage.class);

    private final ProjectTemplatesLoader templatesLoader;
    private final Repositories repositories;

    public CreateRepositoryStage(
            Repositories repositories,
            ProjectTemplatesLoader templatesLoader) {

        this.repositories = repositories;
        this.templatesLoader = templatesLoader;
    }

    @Override
    public CreateProjectContext execute(CreateProjectContext context) {
        ProjectInfo projectInfo = context.projectInfo();

        Objects.requireNonNull(context, "Context cannot be null");
        Objects.requireNonNull(projectInfo, "Project needs to be already set");
        Objects.requireNonNull(context.createProject(), "CreateProject needs to be already set");

        CreateProject project = context.createProject();
        Optional<ProjectTemplate> templateOpt = templatesLoader.load(project.templateId());
        if (templateOpt.isEmpty()) {
            return context;
        }

        ProjectTemplate template = templateOpt.get();
        ProjectRepository projectRepository = template.repository();

        if (projectRepository != null) {
            ProjectRepositoryRepository repository =
                    repositories.newProjectRepositoryRepository(projectInfo.id());

            DBRepositoryInfo dbRepositoryInfo = new DBRepositoryInfo(
                    projectRepository.type(), projectRepository.finishedSessionDetectionFile());

            repository.insert(dbRepositoryInfo);

            LOG.info("Linked project repository: workspace_id={} project_id={}",
                    projectInfo.workspaceId(), projectInfo.id());
        }

        return context;
    }
}
