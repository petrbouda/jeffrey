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

package pbouda.jeffrey.resources;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.manager.workspace.WorkspacesManager.CreateWorkspaceRequest;
import pbouda.jeffrey.manager.workspace.mirror.MirroringWorkspacesManager;
import pbouda.jeffrey.manager.workspace.WorkspaceManager;

import java.net.URI;
import java.util.List;

public class MirroringWorkspacesResource {

    private final MirroringWorkspacesManager.Factory workspacesManagerFactory;

    public record WorkspaceResponse(
            String id,
            String name,
            String description,
            int projectCount) {
    }

    public record MirrorWorkspacesRequest(String remoteUrl, List<String> workspaceIds) {
    }

    public record ListMirroredWorkspacesRequest(String remoteUrl) {
    }

    public MirroringWorkspacesResource(MirroringWorkspacesManager.Factory workspacesManagerFactory) {
        this.workspacesManagerFactory = workspacesManagerFactory;
    }

    @POST
    @Path("/list")
    public List<WorkspaceResponse> listMirroredWorkspaces(ListMirroredWorkspacesRequest request) {
        URI remoteUri = URI.create(request.remoteUrl());
        MirroringWorkspacesManager workspacesManager = workspacesManagerFactory.apply(remoteUri);

        return workspacesManager.findAll().stream()
                .map(WorkspaceManager::info)
                .map(this::toWorkspaceResponse)
                .toList();
    }

    private WorkspaceResponse toWorkspaceResponse(WorkspaceInfo workspaceInfo) {
        return new WorkspaceResponse(
                workspaceInfo.id(),
                workspaceInfo.name(),
                workspaceInfo.description(),
                workspaceInfo.projectCount()
        );
    }

    @POST
    @Path("/create")
    public Response mirrorWorkspaces(MirrorWorkspacesRequest request) {
        URI remoteUri = URI.create(request.remoteUrl());
        MirroringWorkspacesManager workspacesManager = workspacesManagerFactory.apply(remoteUri);
        for (String workspaceId : request.workspaceIds()) {
            CreateWorkspaceRequest createRequest = CreateWorkspaceRequest.builder()
                    .workspaceId(workspaceId)
                    .build();

            workspacesManager.create(createRequest);
        }
        return Response.status(Response.Status.CREATED)
                .build();
    }
}
