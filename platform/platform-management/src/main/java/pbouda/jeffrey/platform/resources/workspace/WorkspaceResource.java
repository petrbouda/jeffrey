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

package pbouda.jeffrey.platform.resources.workspace;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.queue.PersistentQueue;
import pbouda.jeffrey.platform.resources.response.WorkspaceEventResponse;
import pbouda.jeffrey.platform.resources.response.WorkspaceResponse;
import pbouda.jeffrey.platform.workspace.WorkspaceEventConverter;
import pbouda.jeffrey.profile.ai.heapmcp.service.HeapDumpAnalysisAssistantService;
import pbouda.jeffrey.profile.ai.mcp.service.JfrAnalysisAssistantService;
import pbouda.jeffrey.profile.ai.service.HeapDumpContextExtractor;
import pbouda.jeffrey.profile.ai.service.OqlAssistantService;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.util.List;

public class WorkspaceResource {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceResource.class);

    private final WorkspaceInfo workspaceInfo;
    private final WorkspaceManager workspaceManager;
    private final PersistentQueue<WorkspaceEvent> workspaceEventQueue;
    private final OqlAssistantService oqlAssistantService;
    private final JfrAnalysisAssistantService jfrAnalysisAssistantService;
    private final HeapDumpContextExtractor heapDumpContextExtractor;
    private final HeapDumpAnalysisAssistantService heapDumpAnalysisAssistantService;

    public WorkspaceResource(
            WorkspaceInfo workspaceInfo,
            WorkspaceManager workspaceManager,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue,
            OqlAssistantService oqlAssistantService,
            JfrAnalysisAssistantService jfrAnalysisAssistantService,
            HeapDumpContextExtractor heapDumpContextExtractor,
            HeapDumpAnalysisAssistantService heapDumpAnalysisAssistantService) {
        this.workspaceInfo = workspaceInfo;
        this.workspaceManager = workspaceManager;
        this.workspaceEventQueue = workspaceEventQueue;
        this.oqlAssistantService = oqlAssistantService;
        this.jfrAnalysisAssistantService = jfrAnalysisAssistantService;
        this.heapDumpContextExtractor = heapDumpContextExtractor;
        this.heapDumpAnalysisAssistantService = heapDumpAnalysisAssistantService;
    }

    @Path("/projects")
    public WorkspaceProjectsResource projectsResource() {
        return new WorkspaceProjectsResource(
                workspaceInfo,
                workspaceManager,
                oqlAssistantService,
                jfrAnalysisAssistantService,
                heapDumpContextExtractor,
                heapDumpAnalysisAssistantService);
    }

    @DELETE
    public void delete() {
        LOG.debug("Deleting workspace: workspaceId={}", workspaceInfo.id());
        workspaceManager.delete();
    }

    @GET
    public WorkspaceResponse info() {
        LOG.debug("Fetching workspace info: workspaceId={}", workspaceInfo.id());
        return Mappers.toResponse(workspaceManager.resolveInfo());
    }

    @GET
    @Path("/events")
    public List<WorkspaceEventResponse> events() {
        LOG.debug("Listing workspace events: workspaceId={}", workspaceInfo.id());
        return workspaceEventQueue.findAll(workspaceInfo.id()).stream()
                .map(WorkspaceEventConverter::fromQueueEntry)
                .map(Mappers::toEventResponse)
                .toList();
    }
}
