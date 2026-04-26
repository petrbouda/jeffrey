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

package cafe.jeffrey.server.core.web.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.server.core.manager.project.ProjectManager;
import cafe.jeffrey.server.core.manager.project.ProjectsManager;
import cafe.jeffrey.server.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.server.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.server.core.resources.response.ProjectResponse;
import cafe.jeffrey.server.core.resources.response.WorkspaceResponse;
import cafe.jeffrey.server.core.resources.workspace.Mappers;

import java.util.List;

@RestController
@RequestMapping("/api/internal/workspaces")
public class WorkspacesController {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspacesController.class);

    private final WorkspacesManager workspacesManager;

    public WorkspacesController(WorkspacesManager workspacesManager) {
        this.workspacesManager = workspacesManager;
    }

    @GetMapping
    public List<WorkspaceResponse> list() {
        var result = workspacesManager.findAll().stream()
                .map(wm -> {
                    var info = wm.resolveInfo();
                    return new WorkspaceResponse(
                            info.id(),
                            info.name(),
                            info.description(),
                            info.createdAt().toEpochMilli(),
                            info.projectCount(),
                            info.status());
                })
                .toList();
        LOG.debug("Listed workspaces: count={}", result.size());
        return result;
    }

    @GetMapping("/{workspaceId}/projects")
    public List<ProjectResponse> projects(@PathVariable("workspaceId") String workspaceId) {
        WorkspaceManager workspace = workspacesManager.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        ProjectsManager projectsManager = workspace.projectsManager();
        var result = projectsManager.findAll().stream()
                .map(ProjectManager::detailedInfo)
                .map(Mappers::toProjectResponse)
                .toList();
        LOG.debug("Listed projects for workspace: workspaceId={} count={}", workspaceId, result.size());
        return result;
    }
}
