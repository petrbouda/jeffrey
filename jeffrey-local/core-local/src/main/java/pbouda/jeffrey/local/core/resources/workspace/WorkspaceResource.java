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

package pbouda.jeffrey.local.core.resources.workspace;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.local.core.resources.response.WorkspaceEventResponse;
import pbouda.jeffrey.local.core.resources.response.WorkspaceResponse;
import pbouda.jeffrey.profile.resources.ProfileResourceFactory;
import pbouda.jeffrey.local.persistence.model.RemoteWorkspaceInfo;

import java.time.Clock;
import java.util.List;

public class WorkspaceResource {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceResource.class);

    private final RemoteWorkspaceInfo workspaceInfo;
    private final WorkspaceManager workspaceManager;
    private final ProfileResourceFactory profileResourceFactory;
    private final Clock clock;

    public WorkspaceResource(
            RemoteWorkspaceInfo workspaceInfo,
            WorkspaceManager workspaceManager,
            ProfileResourceFactory profileResourceFactory,
            Clock clock) {
        this.workspaceInfo = workspaceInfo;
        this.workspaceManager = workspaceManager;
        this.profileResourceFactory = profileResourceFactory;
        this.clock = clock;
    }

    @Path("/projects")
    public WorkspaceProjectsResource projectsResource() {
        return new WorkspaceProjectsResource(
                workspaceInfo,
                workspaceManager,
                profileResourceFactory,
                clock);
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
        LOG.debug("Fetching workspace events: workspaceId={}", workspaceInfo.id());
        return workspaceManager.events();
    }

}
