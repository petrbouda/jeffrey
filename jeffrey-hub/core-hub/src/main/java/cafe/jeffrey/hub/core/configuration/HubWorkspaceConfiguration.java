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
