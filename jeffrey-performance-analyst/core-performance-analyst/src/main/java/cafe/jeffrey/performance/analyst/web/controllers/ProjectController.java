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

package cafe.jeffrey.performance.analyst.web.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.hub.client.dto.RemoteProjectResponse;
import cafe.jeffrey.performance.analyst.manager.server.HubManager;
import cafe.jeffrey.performance.analyst.resources.response.ProjectResponse;
import cafe.jeffrey.performance.analyst.resources.workspace.Mappers;
import cafe.jeffrey.performance.analyst.web.ServerResolver;

/**
 * Serves a single remote project's info to the Performance Analyst UI (the project-detail
 * page). The analyst is read-only against remote hubs, so unlike microscope's controller it
 * exposes no delete/restore — those RPCs are not available to the analyst.
 */
@RestController
@RequestMapping("/api/internal/hubs/{hubId}/workspaces/{workspaceId}/projects/{projectId}")
public class ProjectController {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectController.class);

    private final ServerResolver resolver;

    public ProjectController(ServerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public ProjectResponse info(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId) {
        HubManager server = resolver.resolveServer(hubId);
        RemoteProjectResponse project = server.project(workspaceId, projectId);
        LOG.debug("Fetching project info: workspaceId={} projectId={}", workspaceId, projectId);
        return Mappers.toProjectResponse(project);
    }

    @GetMapping("/initializing")
    public boolean initializing() {
        return false;
    }
}
