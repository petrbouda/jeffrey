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

package pbouda.jeffrey.platform.resources.workspace;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.profile.ai.service.HeapDumpContextExtractor;
import pbouda.jeffrey.profile.ai.service.OqlAssistantService;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.resources.response.WorkspaceEventResponse;
import pbouda.jeffrey.platform.resources.response.WorkspaceResponse;

import java.util.List;

public class WorkspaceResource {

    private final WorkspaceInfo workspaceInfo;
    private final WorkspaceManager workspaceManager;
    private final OqlAssistantService oqlAssistantService;
    private final HeapDumpContextExtractor heapDumpContextExtractor;

    public WorkspaceResource(
            WorkspaceInfo workspaceInfo,
            WorkspaceManager workspaceManager,
            OqlAssistantService oqlAssistantService,
            HeapDumpContextExtractor heapDumpContextExtractor) {
        this.workspaceInfo = workspaceInfo;
        this.workspaceManager = workspaceManager;
        this.oqlAssistantService = oqlAssistantService;
        this.heapDumpContextExtractor = heapDumpContextExtractor;
    }

    @Path("/projects")
    public WorkspaceProjectsResource projectsResource() {
        return new WorkspaceProjectsResource(
                workspaceInfo,
                workspaceManager,
                oqlAssistantService,
                heapDumpContextExtractor);
    }

    @DELETE
    public void delete() {
        workspaceManager.delete();
    }

    @GET
    public WorkspaceResponse info() {
        return Mappers.toResponse(workspaceInfo);
    }

    @GET
    @Path("/events")
    public List<WorkspaceEventResponse> events() {
        return workspaceManager.workspaceEventManager().findEvents().stream()
                .map(Mappers::toEventResponse)
                .toList();
    }
}
