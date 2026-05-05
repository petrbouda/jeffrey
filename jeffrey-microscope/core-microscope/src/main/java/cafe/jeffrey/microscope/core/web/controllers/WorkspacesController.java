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

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.manager.server.RemoteServerManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.resources.request.CreateWorkspaceRequest;
import cafe.jeffrey.microscope.core.resources.response.WorkspaceEventsResponse;
import cafe.jeffrey.microscope.core.resources.response.WorkspaceResponse;
import cafe.jeffrey.microscope.core.resources.workspace.Mappers;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceReferenceId;

import java.util.List;

@RestController
@RequestMapping("/api/internal/remote-servers/{serverId}/workspaces")
public class WorkspacesController {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspacesController.class);

    private final ProjectManagerResolver resolver;

    public WorkspacesController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public List<WorkspaceResponse> workspaces(@PathVariable("serverId") String serverId) {
        RemoteServerManager server = resolver.resolveServer(serverId);
        var result = server.workspaces().stream()
                .map(Mappers::toResponse)
                .toList();
        LOG.debug("Listed workspaces: serverId={} count={}", serverId, result.size());
        return result;
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceResponse workspace(
            @PathVariable("serverId") String serverId,
            @PathVariable("workspaceId") String workspaceId) {

        WorkspaceManager workspace = resolver.resolveWorkspace(serverId, workspaceId);
        return Mappers.toResponse(workspace.resolveInfo());
    }

    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<Void> delete(
            @PathVariable("serverId") String serverId,
            @PathVariable("workspaceId") String workspaceId) {

        WorkspaceManager workspace = resolver.resolveWorkspace(serverId, workspaceId);
        workspace.delete();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{workspaceId}/events")
    public WorkspaceEventsResponse events(
            @PathVariable("serverId") String serverId,
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {

        LOG.debug("Fetching workspace events: workspaceId={} limit={}", workspaceId, limit);
        return resolver.resolveWorkspace(serverId, workspaceId).events(limit);
    }

    @PostMapping
    public ResponseEntity<WorkspaceResponse> create(
            @PathVariable("serverId") String serverId,
            @RequestBody CreateWorkspaceRequest request) {

        if (request.referenceId() == null || request.referenceId().isBlank()) {
            throw Exceptions.invalidRequest("Reference ID is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw Exceptions.invalidRequest("Workspace name is required");
        }
        String referenceId = request.referenceId().trim();
        if (!WorkspaceReferenceId.isValid(referenceId)) {
            throw Exceptions.invalidRequest(
                    "Invalid workspace reference ID: " + WorkspaceReferenceId.DESCRIPTION);
        }

        RemoteServerManager server = resolver.resolveServer(serverId);
        try {
            WorkspaceInfo created = server.createWorkspace(referenceId, request.name().trim());
            return ResponseEntity.status(HttpStatus.CREATED).body(Mappers.toResponse(created));
        } catch (StatusRuntimeException e) {
            throw mapStatusError(e);
        }
    }

    private static RuntimeException mapStatusError(StatusRuntimeException e) {
        if (e.getStatus().getCode() == Status.Code.ALREADY_EXISTS) {
            return Exceptions.invalidRequest(
                    e.getStatus().getDescription() != null
                            ? e.getStatus().getDescription()
                            : "Workspace already exists on this server");
        }
        if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
            return Exceptions.invalidRequest(
                    e.getStatus().getDescription() != null
                            ? e.getStatus().getDescription()
                            : "Invalid workspace creation request");
        }
        return e;
    }
}
