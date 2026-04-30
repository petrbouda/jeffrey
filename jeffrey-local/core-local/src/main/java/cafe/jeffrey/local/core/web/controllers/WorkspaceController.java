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

package cafe.jeffrey.local.core.web.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.local.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.local.core.resources.response.WorkspaceEventResponse;
import cafe.jeffrey.local.core.resources.response.WorkspaceResponse;
import cafe.jeffrey.local.core.resources.workspace.Mappers;
import cafe.jeffrey.local.core.web.ProjectManagerResolver;

import java.util.List;

@RestController
@RequestMapping("/api/internal/remote-servers/{serverId}/workspaces/{workspaceId}")
public class WorkspaceController {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceController.class);

    private final ProjectManagerResolver resolver;

    public WorkspaceController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public WorkspaceResponse info(@PathVariable("serverId") String serverId, @PathVariable("workspaceId") String workspaceId) {
        LOG.debug("Fetching workspace info: workspaceId={}", workspaceId);
        WorkspaceManager workspace = resolver.resolveWorkspace(serverId, workspaceId);
        return Mappers.toResponse(workspace.resolveInfo());
    }

    @DeleteMapping
    public void delete(@PathVariable("serverId") String serverId, @PathVariable("workspaceId") String workspaceId) {
        LOG.debug("Deleting workspace: workspaceId={}", workspaceId);
        resolver.resolveWorkspace(serverId, workspaceId).delete();
    }

    @GetMapping("/events")
    public List<WorkspaceEventResponse> events(@PathVariable("serverId") String serverId, @PathVariable("workspaceId") String workspaceId) {
        LOG.debug("Fetching workspace events: workspaceId={}", workspaceId);
        return resolver.resolveWorkspace(serverId, workspaceId).events();
    }
}
