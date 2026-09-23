/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.core.web.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.web.response.ProjectResponse;
import cafe.jeffrey.hub.core.web.response.WorkspaceResponse;
import cafe.jeffrey.hub.core.web.response.Mappers;

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
        return workspacesManager.findAll().stream()
                .map(workspace -> Mappers.toResponse(workspace.resolveInfo()))
                .toList();
    }

    @GetMapping("/{workspaceId}/projects")
    public List<ProjectResponse> projects(@PathVariable("workspaceId") String workspaceId) {
        WorkspaceManager workspace = workspacesManager.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found: " + workspaceId));

        ProjectsManager projectsManager = workspace.projectsManager();
        var result = projectsManager.findAll().stream()
                .map(ProjectManager::detailedInfo)
                .map(Mappers::toProjectResponse)
                .toList();
        LOG.debug("Listed projects for workspace: workspace_id={} count={}", workspaceId, result.size());
        return result;
    }
}
