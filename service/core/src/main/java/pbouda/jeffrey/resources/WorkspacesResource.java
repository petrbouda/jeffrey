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

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.resources.response.WorkspaceResponse;
import pbouda.jeffrey.resources.workspace.WorkspaceMappers;
import pbouda.jeffrey.resources.workspace.WorkspaceResource;

import java.util.List;

public class WorkspacesResource {

    public record CreateWorkspaceRequest(
            String id,
            String name,
            String description,
            String location,
            WorkspaceType type) {
    }

    private final CompositeWorkspacesManager workspacesManager;

    public WorkspacesResource(CompositeWorkspacesManager workspacesManager) {
        this.workspacesManager = workspacesManager;
    }

    @Path("/{workspaceId}")
    public WorkspaceResource workspaceResource(
            @PathParam("workspaceId") String workspaceId,
            @QueryParam("type") WorkspaceType type) {

        WorkspaceManager workspaceManager = workspacesManager.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace not found"));

        if (type != null && workspaceManager.resolveInfo().type() != type) {
            throw new BadRequestException("Invalid workspace type");
        }

        return new WorkspaceResource(workspaceManager);
    }

    @GET
    public List<WorkspaceResponse> workspaces(@QueryParam("type") WorkspaceType type) {
        return workspacesManager.findAll().stream()
                .map(WorkspaceManager::resolveInfo)
                .filter(info -> type == null || info.type() == type)
                .map(WorkspaceMappers::toResponse)
                .toList();
    }

    @POST
    public Response createWorkspace(CreateWorkspaceRequest request) {
        String workspaceId;
        if (request.id() == null || request.id().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Workspace ID is required")
                    .build();
        } else {
            workspaceId = request.id().trim();
        }

        String workspaceName;
        if (request.name() == null || request.name().isBlank()) {
            workspaceName = workspaceId;
        } else {
            workspaceName = request.name().trim();
        }

        if (!request.type.isInnerWorkspace()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Only LOCAL or SANDBOX workspace type can be created")
                    .build();
        }

        try {
            WorkspacesManager.CreateWorkspaceRequest createRequest = WorkspacesManager.CreateWorkspaceRequest.builder()
                    .workspaceSourceId(workspaceId)
                    .name(workspaceName)
                    .description(request.description())
                    .location(request.location())
                    .type(request.type())
                    .build();

            WorkspaceInfo workspaceInfo = workspacesManager.create(createRequest);

            return Response.status(Response.Status.CREATED)
                    .entity(WorkspaceMappers.toResponse(workspaceInfo))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage())
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to create workspace: " + e.getMessage())
                    .build();
        }
    }
}
