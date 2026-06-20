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

package cafe.jeffrey.shared.ui.workspace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.hub.client.HubClients;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.shared.ui.workspace.bridge.HubRegistry;
import cafe.jeffrey.shared.ui.workspace.bridge.RecordingProfileInfoProvider;
import cafe.jeffrey.shared.ui.workspace.bridge.RemoteProjectAccess;
import cafe.jeffrey.shared.ui.workspace.bridge.WorkspaceBrowserAccess;
import cafe.jeffrey.shared.ui.workspace.controller.HubsController;
import cafe.jeffrey.shared.ui.workspace.controller.ProjectController;
import cafe.jeffrey.shared.ui.workspace.controller.ProjectDownloadTaskController;
import cafe.jeffrey.shared.ui.workspace.controller.ProjectInstancesController;
import cafe.jeffrey.shared.ui.workspace.controller.ProjectRepositoryController;
import cafe.jeffrey.shared.ui.workspace.controller.RecordingsController;
import cafe.jeffrey.shared.ui.workspace.controller.WorkspaceProjectsController;
import cafe.jeffrey.shared.ui.workspace.controller.WorkspacesController;

import java.time.Clock;

/**
 * Registers the shared remote-workspace controllers as {@code @Bean}s. The controllers are
 * {@code @RestController}-annotated (Spring 7's {@code RequestMappingHandlerMapping} requires the
 * {@code @Controller} stereotype to treat a bean as a handler — a bare type-level
 * {@code @RequestMapping} is no longer sufficient), but they live in
 * {@code cafe.jeffrey.shared.ui.workspace.controller}, which is outside both apps' component-scan
 * roots ({@code cafe.jeffrey.microscope.core} / {@code cafe.jeffrey.performance.analyst}). They are
 * therefore registered exactly once, via these explicit {@code @Bean} methods, with no
 * component-scan pickup. Each deployment {@code @Import}s this configuration and supplies the bridge
 * beans ({@link RemoteProjectAccess}, {@link RecordingProfileInfoProvider}) plus the shared
 * {@link RecordingsCoreManager} and a {@link Clock}.
 */
@Configuration
public class WorkspacesFeatureConfiguration {

    @Bean
    public ProjectInstancesController workspacesFeatureProjectInstancesController(
            RemoteProjectAccess projectAccess,
            Clock clock) {
        return new ProjectInstancesController(projectAccess, clock);
    }

    @Bean
    public ProjectRepositoryController workspacesFeatureProjectRepositoryController(
            RemoteProjectAccess projectAccess,
            Clock clock) {
        return new ProjectRepositoryController(projectAccess, clock);
    }

    @Bean
    public ProjectDownloadTaskController workspacesFeatureProjectDownloadTaskController(
            RemoteProjectAccess projectAccess) {
        return new ProjectDownloadTaskController(projectAccess);
    }

    @Bean
    public RecordingsController workspacesFeatureRecordingsController(
            RecordingsCoreManager recordingsCoreManager,
            RecordingProfileInfoProvider profileInfoProvider) {
        return new RecordingsController(recordingsCoreManager, profileInfoProvider);
    }

    @Bean
    public HubsController workspacesFeatureHubsController(
            HubRegistry hubRegistry,
            HubClients.Factory clientsFactory) {
        return new HubsController(hubRegistry, clientsFactory);
    }

    @Bean
    public WorkspacesController workspacesFeatureWorkspacesController(WorkspaceBrowserAccess workspaceBrowserAccess) {
        return new WorkspacesController(workspaceBrowserAccess);
    }

    @Bean
    public ProjectController workspacesFeatureProjectController(WorkspaceBrowserAccess workspaceBrowserAccess) {
        return new ProjectController(workspaceBrowserAccess);
    }

    @Bean
    public WorkspaceProjectsController workspacesFeatureWorkspaceProjectsController(WorkspaceBrowserAccess workspaceBrowserAccess) {
        return new WorkspaceProjectsController(workspaceBrowserAccess);
    }
}
