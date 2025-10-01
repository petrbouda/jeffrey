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

package pbouda.jeffrey.resources.pub;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import pbouda.jeffrey.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.resources.response.WorkspaceResponse;
import pbouda.jeffrey.resources.workspace.Mappers;

import java.util.List;

public class WorkspacesPublicResource {

    private final CompositeWorkspacesManager workspacesManager;

    public WorkspacesPublicResource(CompositeWorkspacesManager workspacesManager) {
        this.workspacesManager = workspacesManager;
    }

    @Path("/{workspaceId}")
    public WorkspacePublicResource workspaceResource(@PathParam("workspaceId") String workspaceId) {
        WorkspaceManager workspaceManager = workspacesManager.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace not found"));

        // Public API of Jeffrey can provide only LOCAL Workspaces
        if (workspaceManager.resolveInfo().type() != WorkspaceType.LOCAL) {
            throw new NotFoundException("Workspace not found");
        }

        return new WorkspacePublicResource(workspaceManager);
    }

    @GET
    public List<WorkspaceResponse> workspaces() {
        // Public API of Jeffrey can provide only LOCAL Workspaces
        return workspacesManager.findAll().stream()
                .map(WorkspaceManager::resolveInfo)
                .filter(info -> info.type() == WorkspaceType.LOCAL)
                .map(Mappers::toResponse)
                .toList();
    }
}
