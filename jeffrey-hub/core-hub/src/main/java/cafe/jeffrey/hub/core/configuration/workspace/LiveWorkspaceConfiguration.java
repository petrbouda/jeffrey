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

package cafe.jeffrey.hub.core.configuration.workspace;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.core.manager.workspace.LiveWorkspacesManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.LiveWorkspaceManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.WorkspaceRepository;
import cafe.jeffrey.hub.core.HubJeffreyDirs;

import java.time.Clock;

@Configuration
public class LiveWorkspaceConfiguration {

    public static final String LIVE_WORKSPACE_TYPE = "LIVE_WORKSPACE_FACTORY_TYPE";

    @Bean(LIVE_WORKSPACE_TYPE)
    public WorkspaceManager.Factory workspaceManagerFactory(
            Clock applicationClock,
            HubJeffreyDirs jeffreyDirs,
            HubPlatformRepositories platformRepositories,
            RepositoryStorage.Factory repositoryStorageFactory,
            @Qualifier(HubWorkspaceConfiguration.COMMON_PROJECTS_TYPE) ProjectsManager.Factory projectsManagerFactory) {

        return workspaceInfo -> {
            WorkspaceRepository workspaceRepository = platformRepositories.newWorkspaceRepository(workspaceInfo.id());
            return new LiveWorkspaceManager(
                    applicationClock, jeffreyDirs, workspaceInfo, workspaceRepository, platformRepositories,
                    projectsManagerFactory, repositoryStorageFactory);
        };
    }

    @Bean
    public LiveWorkspacesManager liveWorkspaceManager(
            Clock applicationClock,
            HubPlatformRepositories platformRepositories,
            @Qualifier(LIVE_WORKSPACE_TYPE) WorkspaceManager.Factory workspaceManagerFactory) {

        return new LiveWorkspacesManager(applicationClock, platformRepositories.newWorkspacesRepository(), workspaceManagerFactory);
    }
}
