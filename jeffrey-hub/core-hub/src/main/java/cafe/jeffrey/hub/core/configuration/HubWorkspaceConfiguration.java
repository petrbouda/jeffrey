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

import cafe.jeffrey.hub.core.appinitializer.DefaultWorkspaceInitializer;
import cafe.jeffrey.hub.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.hub.core.configuration.properties.WorkspacesProperties;
import cafe.jeffrey.hub.core.manager.project.HubProjectManager;
import cafe.jeffrey.hub.core.manager.project.HubProjectsManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.core.manager.workspace.HubWorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.HubWorkspacesManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.liveness.SessionLiveness;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the workspace tree over the volume: the one {@link HubWorkspacesManager} at the
 * workspaces root, and the factories it builds a {@link WorkspaceManager}, its
 * {@link ProjectsManager} and each {@link ProjectManager} from, one per directory.
 */
@Configuration
public class HubWorkspaceConfiguration {

    @Bean
    public ProjectManager.Factory projectManagerFactory(
            FilesystemVolume workspacesVolume, SessionLiveness sessionLiveness) {
        return project -> new HubProjectManager(project, workspacesVolume, sessionLiveness);
    }

    @Bean
    public ProjectsManager.Factory projectsManagerFactory(ProjectManager.Factory projectManagerFactory) {
        return workspace -> new HubProjectsManager(workspace, projectManagerFactory);
    }

    @Bean
    public WorkspaceManager.Factory workspaceManagerFactory(
            FilesystemVolume workspacesVolume, ProjectsManager.Factory projectsManagerFactory) {
        return workspace -> new HubWorkspaceManager(workspace, workspacesVolume, projectsManagerFactory);
    }

    @Bean
    public HubWorkspacesManager workspacesManager(
            FilesystemVolume workspacesVolume,
            Clock applicationClock,
            WorkspacesProperties workspacesProperties,
            WorkspaceManager.Factory workspaceManagerFactory) {
        return new HubWorkspacesManager(
                workspacesVolume, applicationClock, workspacesProperties.isAutoCreate(), workspaceManagerFactory);
    }

    @Bean
    public DefaultWorkspaceInitializer defaultWorkspaceInitializer(
            WorkspacesManager workspacesManager, DefaultWorkspaceProperties defaultWorkspaceProperties) {
        return new DefaultWorkspaceInitializer(workspacesManager, defaultWorkspaceProperties);
    }
}
