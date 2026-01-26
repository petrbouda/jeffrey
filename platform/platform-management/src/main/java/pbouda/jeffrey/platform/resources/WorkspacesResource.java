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

package pbouda.jeffrey.platform.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import pbouda.jeffrey.platform.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.platform.resources.response.WorkspaceResponse;
import pbouda.jeffrey.platform.resources.workspace.Mappers;
import pbouda.jeffrey.platform.resources.workspace.WorkspaceResource;
import pbouda.jeffrey.profile.ai.mcp.service.JfrAnalysisAssistantService;
import pbouda.jeffrey.profile.ai.service.HeapDumpContextExtractor;
import pbouda.jeffrey.profile.ai.service.OqlAssistantService;
import pbouda.jeffrey.shared.common.exception.Exceptions;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;

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
    private final OqlAssistantService oqlAssistantService;
    private final JfrAnalysisAssistantService jfrAnalysisAssistantService;
    private final HeapDumpContextExtractor heapDumpContextExtractor;

    public WorkspacesResource(
            CompositeWorkspacesManager workspacesManager,
            OqlAssistantService oqlAssistantService,
            JfrAnalysisAssistantService jfrAnalysisAssistantService,
            HeapDumpContextExtractor heapDumpContextExtractor) {
        this.workspacesManager = workspacesManager;
        this.oqlAssistantService = oqlAssistantService;
        this.jfrAnalysisAssistantService = jfrAnalysisAssistantService;
        this.heapDumpContextExtractor = heapDumpContextExtractor;
    }

    @Path("/{workspaceId}")
    public WorkspaceResource workspaceResource(
            @PathParam("workspaceId") String workspaceId,
            @QueryParam("type") WorkspaceType type) {

        WorkspaceManager workspaceManager = workspacesManager.findById(workspaceId)
                .orElseThrow(() -> Exceptions.workspaceNotFound(workspaceId));

        WorkspaceInfo workspaceInfo = workspaceManager.resolveInfo();
        if (type != null && workspaceInfo.type() != type) {
            throw Exceptions.invalidRequest("Invalid workspace type: expected " + type + " but found " + workspaceInfo.type());
        }

        return new WorkspaceResource(
                workspaceInfo,
                workspaceManager,
                oqlAssistantService,
                jfrAnalysisAssistantService,
                heapDumpContextExtractor);
    }

    @GET
    public List<WorkspaceResponse> workspaces(@QueryParam("type") WorkspaceType type) {
        List<WorkspaceResponse> workspaces = workspacesManager.findAll().stream()
                .map(WorkspaceManager::resolveInfo)
                .filter(info -> type == null || info.type() == type)
                .map(Mappers::toResponse)
                .toList();

        return workspaces;
    }

    @POST
    public Response createWorkspace(CreateWorkspaceRequest request) {
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

        if (request.type() == null || !request.type().isInnerWorkspace()) {
            throw Exceptions.invalidRequest("Only LIVE or SANDBOX workspace type can be created");
        }

        WorkspacesManager.CreateWorkspaceRequest createRequest = WorkspacesManager.CreateWorkspaceRequest.builder()
                .workspaceSourceId(workspaceId)
                .name(workspaceName)
                .description(request.description())
                .location(request.location())
                .type(request.type())
                .build();

        WorkspaceInfo workspaceInfo = workspacesManager.create(createRequest);

        return Response.status(Response.Status.CREATED)
                .entity(Mappers.toResponse(workspaceInfo))
                .build();
    }
}
