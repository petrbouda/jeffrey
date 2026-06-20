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

package cafe.jeffrey.shared.ui.workspace.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.shared.ui.workspace.bridge.WorkspaceBrowserAccess;
import cafe.jeffrey.shared.ui.workspace.dto.ProjectResponse;

/**
 * Shared WorkspaceBrowser controller serving a single remote project's info. Both deployments
 * register it via {@code WorkspacesFeatureConfiguration} and supply a {@link WorkspaceBrowserAccess}
 * bridge. Write operations (delete/restore) are microscope-only and live in a deployment-specific
 * supplementary controller.
 */
@RestController
@RequestMapping("/api/internal/hubs/{hubId}/workspaces/{workspaceId}/projects/{projectId}")
public class ProjectController {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectController.class);

    private final WorkspaceBrowserAccess access;

    public ProjectController(WorkspaceBrowserAccess access) {
        this.access = access;
    }

    @GetMapping
    public ProjectResponse info(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId) {
        LOG.debug("Fetching project info: workspaceId={} projectId={}", workspaceId, projectId);
        return access.project(hubId, workspaceId, projectId);
    }

    @GetMapping("/initializing")
    public boolean initializing() {
        return false;
    }
}
