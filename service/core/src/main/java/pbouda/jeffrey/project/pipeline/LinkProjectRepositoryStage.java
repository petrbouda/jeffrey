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
import pbouda.jeffrey.manager.RepositoryManager;
import pbouda.jeffrey.model.RepositoryInfo;
import pbouda.jeffrey.project.ProjectRepository;
import pbouda.jeffrey.project.ProjectTemplate;
import pbouda.jeffrey.project.ProjectTemplatesLoader;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public class LinkProjectRepositoryStage implements Stage<CreateProjectContext> {

    private static final Logger LOG = LoggerFactory.getLogger(LinkProjectRepositoryStage.class);

    private static final String PROJECT_NAME_REPLACE = "${projectName}";

    private final RepositoryManager.Factory repositoryManagerFactory;
    private final ProjectTemplatesLoader templatesLoader;

    public LinkProjectRepositoryStage(
            RepositoryManager.Factory repositoryManagerFactory,
            ProjectTemplatesLoader templatesLoader) {

        this.repositoryManagerFactory = repositoryManagerFactory;
        this.templatesLoader = templatesLoader;
    }

    @Override
    public CreateProjectContext execute(CreateProjectContext context) {
        Objects.requireNonNull(context, "Context cannot be null");
        Objects.requireNonNull(context.projectInfo(), "Project needs to be already set");

        Optional<ProjectTemplate> templateOpt = templatesLoader.load(context.templateId());
        if (templateOpt.isEmpty()) {
            return context;
        }

        ProjectTemplate template = templateOpt.get();
        ProjectRepository projectRepository = template.repository();

        if (projectRepository != null) {
            Path repositoryPath = normalizePath(context.projectInfo(), projectRepository.path());
            RepositoryInfo repositoryInfo = new RepositoryInfo(
                    repositoryPath,
                    projectRepository.type(),
                    projectRepository.finishedSessionDetectionFile());

            repositoryManagerFactory.apply(context.projectInfo())
                    .createOrReplace(projectRepository.create(), repositoryInfo);

            LOG.info("Linked project repository: repository_path={} project_id={}",
                    repositoryPath, context.projectInfo().id());
        }

        return context;
    }

    private static Path normalizePath(ProjectInfo projectInfo, String repositoryPath) {
        String path = repositoryPath;
        if (path.contains(PROJECT_NAME_REPLACE)) {
            String projectName = projectInfo.name();
            String normalizedProjectName = projectName.toLowerCase().replaceAll(" ", "-");
            path = path.replace(PROJECT_NAME_REPLACE, normalizedProjectName);
        }
        return Path.of(path);
    }
}
