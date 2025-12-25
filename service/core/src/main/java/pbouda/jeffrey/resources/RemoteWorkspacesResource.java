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
import pbouda.jeffrey.common.model.workspace.WorkspaceLocation;
import pbouda.jeffrey.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.common.exception.Exceptions;
import pbouda.jeffrey.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.manager.workspace.WorkspacesManager.CreateWorkspaceRequest;
import pbouda.jeffrey.manager.workspace.remote.RemoteWorkspaceClient;

import java.net.URI;
import java.util.List;

public class RemoteWorkspacesResource {

    private final RemoteWorkspaceClient.Factory remoteWorkspacesManagerFactory;
    private final CompositeWorkspacesManager workspacesManager;

    public record WorkspaceResponse(
            String id,
            String name,
            String description,
            int projectCount) {
    }

    public record RemoteWorkspacesRequest(String remoteUrl, List<String> workspaceIds) {
    }

    public record ListRemoteWorkspacesRequest(String remoteUrl) {
    }

    public RemoteWorkspacesResource(
            RemoteWorkspaceClient.Factory remoteWorkspacesManagerFactory,
            CompositeWorkspacesManager workspacesManager) {

        this.remoteWorkspacesManagerFactory = remoteWorkspacesManagerFactory;
        this.workspacesManager = workspacesManager;
    }

    @POST
    @Path("/list")
    public List<WorkspaceResponse> listRemoteWorkspaces(ListRemoteWorkspacesRequest request) {
        if (request.remoteUrl() == null || request.remoteUrl().isBlank()) {
            throw Exceptions.invalidRequest("Remote URL is required");
        }
        URI remoteUri = URI.create(request.remoteUrl());
        RemoteWorkspaceClient remoteWorkspaceClient = remoteWorkspacesManagerFactory.apply(remoteUri);
        return remoteWorkspaceClient.allWorkspaces().stream()
                .map(this::toWorkspaceResponse)
                .toList();
    }

    @POST
    @Path("/create")
    public Response createRemote(RemoteWorkspacesRequest request) {
        if (request.remoteUrl() == null || request.remoteUrl().isBlank()) {
            throw Exceptions.invalidRequest("Remote URL is required");
        }
        if (request.workspaceIds() == null || request.workspaceIds().isEmpty()) {
            throw Exceptions.invalidRequest("At least one workspace ID is required");
        }
        for (String workspaceId : request.workspaceIds()) {
            CreateWorkspaceRequest createRequest = CreateWorkspaceRequest.builder()
                    .workspaceId(workspaceId)
                    .type(WorkspaceType.REMOTE)
                    .baseLocation(WorkspaceLocation.of(request.remoteUrl()))
                    .build();

            workspacesManager.create(createRequest);
        }
        return Response.status(Response.Status.CREATED).build();
    }

    private WorkspaceResponse toWorkspaceResponse(pbouda.jeffrey.resources.response.WorkspaceResponse workspaceInfo) {
        return new WorkspaceResponse(
                workspaceInfo.id(),
                workspaceInfo.name(),
                workspaceInfo.description(),
                workspaceInfo.projectCount()
        );
    }
}
