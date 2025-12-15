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

package pbouda.jeffrey.configuration.workspace;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import pbouda.jeffrey.common.pipeline.Pipeline;
import pbouda.jeffrey.configuration.AppConfiguration;
import pbouda.jeffrey.configuration.properties.ProjectProperties;
import pbouda.jeffrey.manager.project.CommonProjectsManager;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.manager.project.ProjectsManager;
import pbouda.jeffrey.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.manager.workspace.LiveWorkspacesManager;
import pbouda.jeffrey.manager.workspace.RemoteWorkspacesManager;
import pbouda.jeffrey.manager.workspace.SandboxWorkspacesManager;
import pbouda.jeffrey.project.pipeline.*;
import pbouda.jeffrey.project.template.ProjectTemplatesLoader;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.scheduler.JobDefinitionLoader;

import java.time.Clock;

@Configuration
@Import(AppConfiguration.class)
public class WorkspaceConfiguration {

    public static final String COMMON_PROJECTS_TYPE = "COMMON_PROJECTS_FACTORY_TYPE";

    @Bean
    public CompositeWorkspacesManager compositeWorkspacesManager(
            Repositories repositories,
            ObjectFactory<SandboxWorkspacesManager> sandboxWorkspacesManager,
            ObjectFactory<LiveWorkspacesManager> liveWorkspacesManager,
            ObjectFactory<RemoteWorkspacesManager> remoteWorkspacesManager) {

        return new CompositeWorkspacesManager(
                repositories.newWorkspacesRepository(),
                sandboxWorkspacesManager,
                remoteWorkspacesManager,
                liveWorkspacesManager);
    }

    @Bean(COMMON_PROJECTS_TYPE)
    public ProjectsManager.Factory projectsManagerFactory(
            ProjectProperties projectProperties,
            Repositories repositories,
            ProjectManager.Factory projectManagerFactory,
            ProjectTemplatesLoader projectTemplatesLoader,
            JobDefinitionLoader jobDefinitionLoader,
            Clock clock) {

        return workspaceInfo -> {
            Pipeline<CreateProjectContext> createProjectPipeline = new ProjectCreatePipeline()
                    .addStage(new CreateProjectStage(workspaceInfo, repositories.newProjectsRepository(), projectProperties, clock))
                    .addStage(new CreateRepositoryStage(repositories, projectTemplatesLoader))
                    .addStage(new AddProjectJobsStage(repositories, projectTemplatesLoader, jobDefinitionLoader));

            return new CommonProjectsManager(
                    workspaceInfo,
                    createProjectPipeline,
                    repositories,
                    projectManagerFactory);
        };
    }
}
