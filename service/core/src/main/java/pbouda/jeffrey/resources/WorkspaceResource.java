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

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import pbouda.jeffrey.common.model.WorkspaceInfo;
import pbouda.jeffrey.manager.WorkspacesManager;
import pbouda.jeffrey.resources.util.InstantUtils;

import java.util.List;

public class WorkspaceResource {

    public record WorkspaceResponse(
            String id,
            String name,
            String description,
            String path,
            boolean enabled,
            String createdAt,
            int projectCount) {
    }

    public record CreateWorkspaceRequest(
            String id,
            String name,
            String description,
            String path) {
    }

    private final WorkspacesManager workspacesManager;

    public WorkspaceResource(WorkspacesManager workspacesManager) {
        this.workspacesManager = workspacesManager;
    }

    @GET
    public List<WorkspaceResponse> workspaces() {
        return workspacesManager.all().stream()
                .map(workspace -> new WorkspaceResponse(
                        workspace.id(),
                        workspace.name(),
                        workspace.description(),
                        workspace.path(),
                        workspace.enabled(),
                        InstantUtils.formatInstant(workspace.createdAt()),
                        workspace.projectCount()
                ))
                .toList();
    }

    @POST
    public Response createWorkspace(CreateWorkspaceRequest request) {
        if (request.id() == null || request.id().trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Workspace ID is required")
                    .build();
        }
        
        if (request.name() == null || request.name().trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Workspace name is required")
                    .build();
        }

        try {
            WorkspaceInfo workspaceInfo = workspacesManager.create(
                    request.id(),
                    request.name(),
                    request.description(),
                    request.path());

            WorkspaceResponse response = new WorkspaceResponse(
                    workspaceInfo.id(),
                    workspaceInfo.name(),
                    workspaceInfo.description(),
                    workspaceInfo.path(),
                    workspaceInfo.enabled(),
                    InstantUtils.formatInstant(workspaceInfo.createdAt()),
                    workspaceInfo.projectCount());

            return Response.status(Response.Status.CREATED)
                    .entity(response)
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

    @DELETE
    @Path("/{workspaceId}")
    public Response deleteWorkspace(@PathParam("workspaceId") String workspaceId) {
        if (workspaceId == null || workspaceId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Workspace ID is required")
                    .build();
        }

        try {
            boolean deleted = workspacesManager.delete(workspaceId);
            if (deleted) {
                return Response.noContent().build();
            } else {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Workspace not found")
                        .build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to delete workspace: " + e.getMessage())
                    .build();
        }
    }
}
