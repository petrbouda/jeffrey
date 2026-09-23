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

package cafe.jeffrey.microscope.core.web;

import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.recordings.core.RecordingsDownloadManager;
import cafe.jeffrey.hub.client.manager.RemoteInstancesManager;
import cafe.jeffrey.hub.client.manager.RepositoryManager;
import cafe.jeffrey.shared.ui.hub.bridge.RemoteProjectAccess;

/**
 * Microscope's {@link RemoteProjectAccess} bridge: resolves the per-project remote managers via the
 * existing {@link ProjectManagerResolver} → {@link ProjectManager} chain.
 */
public class MicroscopeRemoteProjectAccess implements RemoteProjectAccess {

    private final ProjectManagerResolver resolver;

    public MicroscopeRemoteProjectAccess(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public RemoteInstancesManager instancesManager(String hubId, String workspaceId, String projectId) {
        return projectManager(hubId, workspaceId, projectId).instancesManager();
    }

    @Override
    public RepositoryManager repositoryManager(String hubId, String workspaceId, String projectId) {
        return projectManager(hubId, workspaceId, projectId).repositoryManager();
    }

    @Override
    public RecordingsDownloadManager recordingsDownloadManager(String hubId, String workspaceId, String projectId) {
        return projectManager(hubId, workspaceId, projectId).recordingsDownloadManager();
    }

    private ProjectManager projectManager(String hubId, String workspaceId, String projectId) {
        return resolver.resolve(hubId, workspaceId, projectId).projectManager();
    }
}
