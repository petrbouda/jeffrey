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

package cafe.jeffrey.performance.analyst.web;

import cafe.jeffrey.recordings.core.RecordingsDownloadManager;
import cafe.jeffrey.hub.client.manager.RemoteInstancesManager;
import cafe.jeffrey.hub.client.manager.RepositoryManager;
import cafe.jeffrey.shared.ui.workspace.bridge.RemoteProjectAccess;

/**
 * Performance Analyst's {@link RemoteProjectAccess} bridge: delegates straight to the existing
 * {@link RemoteProjectResolver}, which builds the per-project remote managers on the shared gRPC
 * client set.
 */
public class AnalystRemoteProjectAccess implements RemoteProjectAccess {

    private final RemoteProjectResolver resolver;

    public AnalystRemoteProjectAccess(RemoteProjectResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public RemoteInstancesManager instancesManager(String hubId, String workspaceId, String projectId) {
        return resolver.instancesManager(hubId, workspaceId, projectId);
    }

    @Override
    public RepositoryManager repositoryManager(String hubId, String workspaceId, String projectId) {
        return resolver.repositoryManager(hubId, workspaceId, projectId);
    }

    @Override
    public RecordingsDownloadManager recordingsDownloadManager(String hubId, String workspaceId, String projectId) {
        return resolver.recordingsDownloadManager(hubId, workspaceId, projectId);
    }
}
