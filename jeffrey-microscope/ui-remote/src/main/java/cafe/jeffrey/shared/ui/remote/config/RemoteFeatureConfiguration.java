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

package cafe.jeffrey.shared.ui.remote.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.shared.ui.remote.bridge.RemoteRegistry;
import cafe.jeffrey.shared.ui.remote.bridge.RecordingProfileInfoProvider;
import cafe.jeffrey.shared.ui.remote.bridge.RemoteProjectAccess;
import cafe.jeffrey.shared.ui.remote.bridge.RemoteBrowserAccess;
import cafe.jeffrey.shared.ui.remote.controller.RemoteController;
import cafe.jeffrey.shared.ui.remote.controller.ProjectDownloadTaskController;
import cafe.jeffrey.shared.ui.remote.controller.ProjectInstancesController;
import cafe.jeffrey.shared.ui.remote.controller.ProjectRepositoryController;
import cafe.jeffrey.shared.ui.remote.controller.RecordingsController;
import cafe.jeffrey.shared.ui.remote.controller.WorkspaceProjectsController;
import cafe.jeffrey.shared.ui.remote.controller.WorkspacesController;


/**
 * Registers the shared remote-workspace controllers as {@code @Bean}s. The controllers are
 * {@code @RestController}-annotated (Spring 7's {@code RequestMappingHandlerMapping} requires the
 * {@code @Controller} stereotype to treat a bean as a handler — a bare type-level
 * {@code @RequestMapping} is no longer sufficient), but they live in
 * {@code cafe.jeffrey.shared.ui.remote.controller}, which is outside the app's component-scan
 * root ({@code cafe.jeffrey.microscope.core}). They are therefore registered exactly once, via these
 * explicit {@code @Bean} methods, with no component-scan pickup. Each deployment
 * {@code @Import}s this configuration and supplies the bridge
 * beans ({@link RemoteProjectAccess}, {@link RecordingProfileInfoProvider}) plus the shared
 * {@link RecordingsCoreManager}.
 */
@Configuration
public class RemoteFeatureConfiguration {

    @Bean
    public ProjectInstancesController remoteFeatureProjectInstancesController(RemoteProjectAccess projectAccess) {
        return new ProjectInstancesController(projectAccess);
    }

    @Bean
    public ProjectRepositoryController remoteFeatureProjectRepositoryController(RemoteProjectAccess projectAccess) {
        return new ProjectRepositoryController(projectAccess);
    }

    @Bean
    public ProjectDownloadTaskController remoteFeatureProjectDownloadTaskController(
            RemoteProjectAccess projectAccess) {
        return new ProjectDownloadTaskController(projectAccess);
    }

    @Bean
    public RecordingsController remoteFeatureRecordingsController(
            RecordingsCoreManager recordingsCoreManager,
            RecordingProfileInfoProvider profileInfoProvider) {
        return new RecordingsController(recordingsCoreManager, profileInfoProvider);
    }

    @Bean
    public RemoteController remoteFeatureRemoteController(RemoteRegistry remoteRegistry) {
        return new RemoteController(remoteRegistry);
    }

    @Bean
    public WorkspacesController remoteFeatureWorkspacesController(RemoteBrowserAccess remoteBrowserAccess) {
        return new WorkspacesController(remoteBrowserAccess);
    }

    @Bean
    public WorkspaceProjectsController remoteFeatureWorkspaceProjectsController(RemoteBrowserAccess remoteBrowserAccess) {
        return new WorkspaceProjectsController(remoteBrowserAccess);
    }
}
