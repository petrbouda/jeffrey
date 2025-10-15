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

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pbouda.jeffrey.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.manager.project.ProjectsManager;
import pbouda.jeffrey.manager.workspace.LocalWorkspacesManager;
import pbouda.jeffrey.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.manager.workspace.local.LocalWorkspaceManager;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;

@Configuration
public class LocalWorkspaceConfiguration {

    public static final String LOCAL_WORKSPACE_TYPE = "LOCAL_WORKSPACE_FACTORY_TYPE";
    public static final String LOCAL_PROJECTS_TYPE = "LOCAL_PROJECTS_FACTORY_TYPE";

//    @Bean(LOCAL_PROJECTS_TYPE)
//    public ProjectsManager.Factory projectsManagerFactory() {
//        return workspaceInfo -> new LocalProjectsManager(workspaceInfo);
//    }

    @Bean(LOCAL_WORKSPACE_TYPE)
    public WorkspaceManager.Factory workspaceManagerFactory(
            JeffreyDirs jeffreyDirs,
            Repositories repositories,
            @Qualifier(WorkspaceConfiguration.COMMON_PROJECTS_TYPE) ProjectsManager.Factory projectsManagerFactory) {
        return workspaceInfo -> {
            WorkspaceRepository workspaceRepository = repositories.newWorkspaceRepository(workspaceInfo.id());
            return new LocalWorkspaceManager(jeffreyDirs, workspaceInfo, workspaceRepository, projectsManagerFactory);
        };
    }

    @Bean
    public LocalWorkspacesManager localWorkspaceManager(
            Repositories repositories,
            @Qualifier(LOCAL_WORKSPACE_TYPE) WorkspaceManager.Factory workspaceManagerFactory) {

        return new LocalWorkspacesManager(repositories.newWorkspacesRepository(), workspaceManagerFactory);
    }
}
