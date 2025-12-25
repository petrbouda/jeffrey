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

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import pbouda.jeffrey.platform.configuration.AppConfiguration;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.manager.workspace.SandboxWorkspacesManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.manager.workspace.sandbox.SandboxWorkspaceManager;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;

import java.time.Clock;

@Configuration
@Import(AppConfiguration.class)
public class SandboxWorkspaceConfiguration {

    public static final String SANDBOX_WORKSPACE_TYPE = "SANDBOX_WORKSPACE_FACTORY_TYPE";

    @Bean(SANDBOX_WORKSPACE_TYPE)
    public SandboxWorkspacesManager sandboxWorkspacesManager(
            Clock clock,
            Repositories repositories,
            @Qualifier(WorkspaceConfiguration.COMMON_PROJECTS_TYPE) ProjectsManager.Factory projectsManagerFactory) {

        WorkspaceManager.Factory workspaceManagerFactory = workspaceInfo -> {
            WorkspaceRepository workspaceRepository = repositories.newWorkspaceRepository(workspaceInfo.id());
            return new SandboxWorkspaceManager(workspaceInfo, workspaceRepository, projectsManagerFactory);
        };

        return new SandboxWorkspacesManager(clock, repositories.newWorkspacesRepository(), workspaceManagerFactory);
    }
}
