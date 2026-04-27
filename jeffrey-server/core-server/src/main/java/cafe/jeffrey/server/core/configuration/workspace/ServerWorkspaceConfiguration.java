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

package cafe.jeffrey.server.core.configuration.workspace;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.server.core.configuration.properties.ProjectProperties;
import cafe.jeffrey.server.core.manager.project.LiveProjectsManager;
import cafe.jeffrey.server.core.manager.project.ProjectManager;
import cafe.jeffrey.server.core.manager.project.ProjectsManager;
import cafe.jeffrey.server.core.project.pipeline.CreateProjectContext;
import cafe.jeffrey.server.core.project.pipeline.CreateProjectStage;
import cafe.jeffrey.server.core.project.pipeline.Pipeline;
import cafe.jeffrey.server.core.project.pipeline.ProjectCreatePipeline;
import cafe.jeffrey.server.core.project.pipeline.ProjectPipelineCustomizer;
import cafe.jeffrey.server.persistence.api.ServerPlatformRepositories;

import java.time.Clock;

@Configuration
public class ServerWorkspaceConfiguration {

    public static final String COMMON_PROJECTS_TYPE = "COMMON_PROJECTS_FACTORY_TYPE";

    @Bean(COMMON_PROJECTS_TYPE)
    public ProjectsManager.Factory projectsManagerFactory(
            ProjectProperties projectProperties,
            ServerPlatformRepositories platformRepositories,
            ProjectManager.Factory projectManagerFactory,
            ObjectProvider<ProjectPipelineCustomizer> pipelineCustomizer,
            Clock clock) {

        return workspaceInfo -> {
            Pipeline<CreateProjectContext> createProjectPipeline = new ProjectCreatePipeline()
                    .addStage(new CreateProjectStage(workspaceInfo, platformRepositories.newProjectsRepository(), projectProperties, clock));

            pipelineCustomizer.ifAvailable(customizer -> customizer.customize(createProjectPipeline));

            return new LiveProjectsManager(
                    workspaceInfo,
                    createProjectPipeline,
                    platformRepositories,
                    projectManagerFactory);
        };
    }
}
