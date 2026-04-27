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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.local.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.local.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.local.core.resources.request.CreateWorkspaceRequest;
import cafe.jeffrey.local.core.resources.response.WorkspaceResponse;
import cafe.jeffrey.local.core.resources.workspace.Mappers;
import cafe.jeffrey.local.persistence.api.RemoteWorkspaceInfo;
import cafe.jeffrey.shared.common.exception.Exceptions;

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
    public List<WorkspaceResponse> workspaces() {
        var result = workspacesManager.findAll().stream()
                .map(WorkspaceManager::resolveInfo)
                .map(Mappers::toResponse)
                .toList();
        LOG.debug("Listed workspaces: count={}", result.size());
        return result;
    }

    @PostMapping
    public ResponseEntity<WorkspaceResponse> createWorkspace(@RequestBody CreateWorkspaceRequest request) {
        LOG.debug("Creating workspace: id={} name={}", request.id(), request.name());
        if (request.id() == null || request.id().isBlank()) {
            throw Exceptions.invalidRequest("Workspace ID is required");
        }
        String workspaceId = request.id().trim();

        String workspaceName;
        if (request.name() == null || request.name().isBlank()) {
            workspaceName = workspaceId;
        } else {
            workspaceName = request.name().trim();
        }

        WorkspacesManager.CreateWorkspaceRequest createRequest = WorkspacesManager.CreateWorkspaceRequest.builder()
                .workspaceSourceId(workspaceId)
                .name(workspaceName)
                .description(request.description())
                .build();

        RemoteWorkspaceInfo workspaceInfo = workspacesManager.create(createRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(Mappers.toResponse(workspaceInfo));
    }
}
