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

package pbouda.jeffrey.resources.workspace;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.resources.response.WorkspaceResponse;

import java.util.List;

public class WorkspaceResource {

    public record WorkspaceEventResponse(
            Long eventId,
            String originEventId,
            String projectId,
            String workspaceId,
            WorkspaceEventType eventType,
            String content,
            Long originCreatedAt,
            Long createdAt) {
    }

    private final WorkspaceManager workspaceManager;

    public WorkspaceResource(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    @DELETE
    public void delete() {
        workspaceManager.delete();
    }

    @GET
    public WorkspaceResponse info() {
        return WorkspaceMappers.toResponse(workspaceManager.info());
    }

    @GET
    @Path("/events")
    public List<WorkspaceEventResponse> events() {
        return workspaceManager.workspaceEventManager().findEvents().stream()
                .map(WorkspaceMappers::toEventResponse)
                .toList();
    }
}
