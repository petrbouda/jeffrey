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

package pbouda.jeffrey.platform.configuration.workspace;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import pbouda.jeffrey.platform.project.pipeline.Pipeline;
import pbouda.jeffrey.platform.configuration.AppConfiguration;
import pbouda.jeffrey.platform.configuration.properties.ProjectProperties;
import pbouda.jeffrey.platform.manager.project.CommonProjectsManager;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.platform.manager.workspace.LiveWorkspacesManager;
import pbouda.jeffrey.platform.manager.workspace.RemoteWorkspacesManager;
import pbouda.jeffrey.platform.manager.workspace.SandboxWorkspacesManager;
import pbouda.jeffrey.platform.project.pipeline.AddProjectJobsStage;
import pbouda.jeffrey.platform.project.pipeline.CreateProjectContext;
import pbouda.jeffrey.platform.project.pipeline.CreateProjectStage;
import pbouda.jeffrey.platform.project.pipeline.ProjectCreatePipeline;
import pbouda.jeffrey.platform.project.template.ProjectTemplatesLoader;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.platform.scheduler.JobDefinitionLoader;

import java.time.Clock;

@Configuration
@Import(AppConfiguration.class)
public class WorkspaceConfiguration {

    public static final String COMMON_PROJECTS_TYPE = "COMMON_PROJECTS_FACTORY_TYPE";

    @Bean
    public CompositeWorkspacesManager compositeWorkspacesManager(
            PlatformRepositories platformRepositories,
            ObjectFactory<SandboxWorkspacesManager> sandboxWorkspacesManager,
            ObjectFactory<LiveWorkspacesManager> liveWorkspacesManager,
            ObjectFactory<RemoteWorkspacesManager> remoteWorkspacesManager) {

        return new CompositeWorkspacesManager(
                platformRepositories.newWorkspacesRepository(),
                sandboxWorkspacesManager,
                remoteWorkspacesManager,
                liveWorkspacesManager);
    }

    @Bean(COMMON_PROJECTS_TYPE)
    public ProjectsManager.Factory projectsManagerFactory(
            ProjectProperties projectProperties,
            PlatformRepositories platformRepositories,
            ProjectManager.Factory projectManagerFactory,
            ProjectTemplatesLoader projectTemplatesLoader,
            JobDefinitionLoader jobDefinitionLoader,
            Clock clock) {

        return workspaceInfo -> {
            Pipeline<CreateProjectContext> createProjectPipeline = new ProjectCreatePipeline()
                    .addStage(new CreateProjectStage(workspaceInfo, platformRepositories.newProjectsRepository(), projectProperties, clock))
                    .addStage(new AddProjectJobsStage(platformRepositories, projectTemplatesLoader, jobDefinitionLoader));

            return new CommonProjectsManager(
                    workspaceInfo,
                    createProjectPipeline,
                    platformRepositories,
                    projectManagerFactory);
        };
    }
}
