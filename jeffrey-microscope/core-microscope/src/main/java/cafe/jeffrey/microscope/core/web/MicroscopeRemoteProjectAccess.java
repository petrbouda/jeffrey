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

package cafe.jeffrey.microscope.core.web;

import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.recordings.core.RecordingsDownloadManager;
import cafe.jeffrey.hub.client.manager.RemoteInstancesManager;
import cafe.jeffrey.hub.client.manager.RepositoryManager;
import cafe.jeffrey.shared.ui.workspace.bridge.RemoteProjectAccess;

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
