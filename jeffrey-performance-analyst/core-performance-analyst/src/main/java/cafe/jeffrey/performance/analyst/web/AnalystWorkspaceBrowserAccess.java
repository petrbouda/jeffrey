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

import cafe.jeffrey.hub.client.DiscoveryClient;
import cafe.jeffrey.performance.analyst.manager.HubManager;
import cafe.jeffrey.performance.analyst.manager.HubsManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.ui.workspace.bridge.WorkspaceBrowserAccess;
import cafe.jeffrey.shared.ui.workspace.dto.ProjectResponse;
import cafe.jeffrey.shared.ui.workspace.dto.WorkspaceResponse;

import java.util.List;

/**
 * Performance Analyst's {@link WorkspaceBrowserAccess} bridge: resolves a hub's workspaces and
 * projects through the gRPC {@link HubManager} (read-only against the remote hub) and maps them into
 * the shared response DTOs.
 */
public class AnalystWorkspaceBrowserAccess implements WorkspaceBrowserAccess {

    private final HubsManager hubsManager;

    public AnalystWorkspaceBrowserAccess(HubsManager hubsManager) {
        this.hubsManager = hubsManager;
    }

    @Override
    public List<WorkspaceResponse> workspaces(String hubId) {
        return resolveServer(hubId).workspaces().stream()
                .map(Mappers::toResponse)
                .toList();
    }

    @Override
    public WorkspaceResponse workspace(String hubId, String workspaceId) {
        DiscoveryClient.WorkspaceResult result = resolveServer(hubId).workspace(workspaceId);
        if (result.info() == null) {
            throw Exceptions.workspaceNotFound(workspaceId);
        }
        return Mappers.toResponse(result.info());
    }

    @Override
    public WorkspaceResponse createWorkspace(String hubId, String referenceId, String name) {
        return Mappers.toResponse(resolveServer(hubId).createWorkspace(referenceId, name));
    }

    @Override
    public void deleteWorkspace(String hubId, String workspaceId) {
        resolveServer(hubId).deleteWorkspace(workspaceId);
    }

    @Override
    public List<ProjectResponse> projects(String hubId, String workspaceId, boolean includeDeleted) {
        return resolveServer(hubId).projects(workspaceId, includeDeleted).stream()
                .map(Mappers::toProjectResponse)
                .toList();
    }

    @Override
    public ProjectResponse project(String hubId, String workspaceId, String projectId) {
        return Mappers.toProjectResponse(resolveServer(hubId).project(workspaceId, projectId));
    }

    private HubManager resolveServer(String hubId) {
        return hubsManager.findById(hubId)
                .orElseThrow(() -> Exceptions.invalidRequest("Hub not found: " + hubId));
    }
}
