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

package cafe.jeffrey.microscope.core.web.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.resources.response.WorkspaceEventsResponse;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;

/**
 * Microscope-only WorkspaceBrowser endpoint: the workspace event feed. The analyst has no equivalent,
 * so this lives outside the shared {@code WorkspacesController}.
 */
@RestController
@RequestMapping("/api/internal/hubs/{hubId}/workspaces/{workspaceId}")
public class WorkspaceEventsController {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceEventsController.class);

    private final ProjectManagerResolver resolver;

    public WorkspaceEventsController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/events")
    public WorkspaceEventsResponse events(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {

        LOG.debug("Fetching workspace events: workspaceId={} limit={}", workspaceId, limit);
        return resolver.resolveWorkspace(hubId, workspaceId).events(limit);
    }
}
