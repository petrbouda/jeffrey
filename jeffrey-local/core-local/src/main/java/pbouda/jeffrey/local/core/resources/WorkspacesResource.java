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

package pbouda.jeffrey.local.core.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.local.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.local.core.resources.request.CreateWorkspaceRequest;
import pbouda.jeffrey.local.core.resources.response.WorkspaceResponse;
import pbouda.jeffrey.local.core.resources.workspace.Mappers;
import pbouda.jeffrey.local.core.resources.workspace.WorkspaceResource;
import pbouda.jeffrey.profile.resources.ProfileResourceFactory;
import pbouda.jeffrey.shared.common.exception.Exceptions;
import pbouda.jeffrey.local.persistence.model.RemoteWorkspaceInfo;

import java.time.Clock;
import java.util.List;

public class WorkspacesResource {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspacesResource.class);

    private final WorkspacesManager workspacesManager;
    private final ProfileResourceFactory profileResourceFactory;
    private final Clock clock;

    public WorkspacesResource(
            WorkspacesManager workspacesManager,
            ProfileResourceFactory profileResourceFactory,
            Clock clock) {
        this.workspacesManager = workspacesManager;
        this.profileResourceFactory = profileResourceFactory;
        this.clock = clock;
    }

    @Path("/{workspaceId}")
    public WorkspaceResource workspaceResource(
            @PathParam("workspaceId") String workspaceId) {

        WorkspaceManager workspaceManager = workspacesManager.findById(workspaceId)
                .orElseThrow(() -> Exceptions.workspaceNotFound(workspaceId));

        RemoteWorkspaceInfo workspaceInfo = workspaceManager.resolveInfo();

        return new WorkspaceResource(
                workspaceInfo,
                workspaceManager,
                profileResourceFactory,
                clock);
    }

    @GET
    public List<WorkspaceResponse> workspaces() {
        var result = workspacesManager.findAll().stream()
                .map(WorkspaceManager::resolveInfo)
                .map(Mappers::toResponse)
                .toList();
        LOG.debug("Listed workspaces: count={}", result.size());
        return result;
    }

    @POST
    public Response createWorkspace(CreateWorkspaceRequest request) {
        LOG.debug("Creating workspace: id={} name={}", request.id(), request.name());
        if (request.id() == null || request.id().isBlank()) {
            throw Exceptions.invalidRequest("Workspace ID is required");
        }
        String workspaceId = request.id().trim();

        String workspaceName;
        if (request.name() == null || request.name().isBlank()) {
            workspaceName = workspaceId;
        } else {
            workspaceName = request.name().trim();
        }

        WorkspacesManager.CreateWorkspaceRequest createRequest = WorkspacesManager.CreateWorkspaceRequest.builder()
                .workspaceSourceId(workspaceId)
                .name(workspaceName)
                .description(request.description())
                .build();

        RemoteWorkspaceInfo workspaceInfo = workspacesManager.create(createRequest);

        return Response.status(Response.Status.CREATED)
                .entity(Mappers.toResponse(workspaceInfo))
                .build();
    }
}
