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

package cafe.jeffrey.shared.ui.hub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.hub.client.HubClients;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.shared.ui.hub.bridge.HubRegistry;
import cafe.jeffrey.shared.ui.hub.bridge.RecordingProfileInfoProvider;
import cafe.jeffrey.shared.ui.hub.bridge.RemoteProjectAccess;
import cafe.jeffrey.shared.ui.hub.bridge.HubBrowserAccess;
import cafe.jeffrey.shared.ui.hub.controller.HubsController;
import cafe.jeffrey.shared.ui.hub.controller.ProjectController;
import cafe.jeffrey.shared.ui.hub.controller.ProjectDownloadTaskController;
import cafe.jeffrey.shared.ui.hub.controller.ProjectInstancesController;
import cafe.jeffrey.shared.ui.hub.controller.ProjectRepositoryController;
import cafe.jeffrey.shared.ui.hub.controller.RecordingsController;
import cafe.jeffrey.shared.ui.hub.controller.WorkspaceProjectsController;
import cafe.jeffrey.shared.ui.hub.controller.WorkspacesController;

import java.time.Clock;

/**
 * Registers the shared remote-workspace controllers as {@code @Bean}s. The controllers are
 * {@code @RestController}-annotated (Spring 7's {@code RequestMappingHandlerMapping} requires the
 * {@code @Controller} stereotype to treat a bean as a handler — a bare type-level
 * {@code @RequestMapping} is no longer sufficient), but they live in
 * {@code cafe.jeffrey.shared.ui.hub.controller}, which is outside the app's component-scan
 * root ({@code cafe.jeffrey.microscope.core}). They are therefore registered exactly once, via these
 * explicit {@code @Bean} methods, with no component-scan pickup. Each deployment
 * {@code @Import}s this configuration and supplies the bridge
 * beans ({@link RemoteProjectAccess}, {@link RecordingProfileInfoProvider}) plus the shared
 * {@link RecordingsCoreManager} and a {@link Clock}.
 */
@Configuration
public class HubsFeatureConfiguration {

    @Bean
    public ProjectInstancesController hubsFeatureProjectInstancesController(
            RemoteProjectAccess projectAccess,
            Clock clock) {
        return new ProjectInstancesController(projectAccess, clock);
    }

    @Bean
    public ProjectRepositoryController hubsFeatureProjectRepositoryController(
            RemoteProjectAccess projectAccess,
            Clock clock) {
        return new ProjectRepositoryController(projectAccess, clock);
    }

    @Bean
    public ProjectDownloadTaskController hubsFeatureProjectDownloadTaskController(
            RemoteProjectAccess projectAccess) {
        return new ProjectDownloadTaskController(projectAccess);
    }

    @Bean
    public RecordingsController hubsFeatureRecordingsController(
            RecordingsCoreManager recordingsCoreManager,
            RecordingProfileInfoProvider profileInfoProvider) {
        return new RecordingsController(recordingsCoreManager, profileInfoProvider);
    }

    @Bean
    public HubsController hubsFeatureHubsController(
            HubRegistry hubRegistry,
            HubClients.Factory clientsFactory) {
        return new HubsController(hubRegistry, clientsFactory);
    }

    @Bean
    public WorkspacesController hubsFeatureWorkspacesController(HubBrowserAccess hubBrowserAccess) {
        return new WorkspacesController(hubBrowserAccess);
    }

    @Bean
    public ProjectController hubsFeatureProjectController(HubBrowserAccess hubBrowserAccess) {
        return new ProjectController(hubBrowserAccess);
    }

    @Bean
    public WorkspaceProjectsController hubsFeatureWorkspaceProjectsController(HubBrowserAccess hubBrowserAccess) {
        return new WorkspaceProjectsController(hubBrowserAccess);
    }
}
