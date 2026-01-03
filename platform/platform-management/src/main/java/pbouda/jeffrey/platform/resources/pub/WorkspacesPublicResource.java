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

package pbouda.jeffrey.platform.resources.pub;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import pbouda.jeffrey.shared.common.exception.Exceptions;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.platform.resources.response.WorkspaceResponse;
import pbouda.jeffrey.platform.resources.workspace.Mappers;
import pbouda.jeffrey.provider.platform.repository.ProfilerRepository;

import java.util.List;

public class WorkspacesPublicResource {

    private final WorkspacesManager workspacesManager;
    private final ProfilerRepository profilerRepository;

    public WorkspacesPublicResource(WorkspacesManager workspacesManager, ProfilerRepository profilerRepository) {
        this.workspacesManager = workspacesManager;
        this.profilerRepository = profilerRepository;
    }

    @Path("/{workspaceId}")
    public WorkspacePublicResource workspaceResource(@PathParam("workspaceId") String workspaceId) {
        WorkspaceManager workspaceManager = workspacesManager.findById(workspaceId)
                .orElseThrow(() -> Exceptions.workspaceNotFound(workspaceId));

        return new WorkspacePublicResource(workspaceManager, profilerRepository);
    }

    @GET
    public List<WorkspaceResponse> workspaces() {
        return workspacesManager.findAll().stream()
                .map(WorkspaceManager::resolveInfo)
                .map(Mappers::toResponse)
                .toList();
    }
}
