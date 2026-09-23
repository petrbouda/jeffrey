/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.core.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.manager.project.HubProjectsManager;
import cafe.jeffrey.hub.core.manager.project.ProjectCreator;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.core.manager.workspace.HubWorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.HubWorkspacesManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.WorkspaceRepository;

import java.time.Clock;

/**
 * Wires the workspace tree: the one {@link HubWorkspacesManager} at the root, and the factories
 * it builds a {@link WorkspaceManager} and its {@link ProjectsManager} from, one per workspace.
 */
@Configuration
public class HubWorkspaceConfiguration {

    @Bean
    public ProjectsManager.Factory projectsManagerFactory(
            HubPlatformRepositories platformRepositories,
            ProjectManager.Factory projectManagerFactory,
            Clock clock) {

        return workspaceInfo -> new HubProjectsManager(
                workspaceInfo,
                new ProjectCreator(workspaceInfo, platformRepositories.newProjectsRepository(), clock),
                platformRepositories,
                projectManagerFactory);
    }

    @Bean
    public WorkspaceManager.Factory workspaceManagerFactory(
            Clock applicationClock,
            HubJeffreyDirs jeffreyDirs,
            HubPlatformRepositories platformRepositories,
            ProjectsManager.Factory projectsManagerFactory) {

        return workspaceInfo -> {
            WorkspaceRepository workspaceRepository = platformRepositories.newWorkspaceRepository(workspaceInfo.id());
            return new HubWorkspaceManager(
                    applicationClock, jeffreyDirs, workspaceInfo, workspaceRepository, projectsManagerFactory);
        };
    }

    @Bean
    public HubWorkspacesManager workspacesManager(
            Clock applicationClock,
            HubPlatformRepositories platformRepositories,
            WorkspaceManager.Factory workspaceManagerFactory) {

        return new HubWorkspacesManager(applicationClock, platformRepositories.newWorkspacesRepository(), workspaceManagerFactory);
    }
}
