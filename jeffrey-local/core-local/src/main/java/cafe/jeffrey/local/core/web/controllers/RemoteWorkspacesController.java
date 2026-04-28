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

import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.local.core.client.RemoteClients;
import cafe.jeffrey.local.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.local.core.manager.workspace.WorkspacesManager.CreateWorkspaceRequest;
import cafe.jeffrey.local.core.resources.request.RemoteWorkspaceConnectionRequest;
import cafe.jeffrey.local.core.resources.request.RemoteWorkspacesRequest;
import cafe.jeffrey.local.core.resources.response.RemoteWorkspaceResponse;
import cafe.jeffrey.local.core.resources.response.WorkspaceResponse;
import cafe.jeffrey.local.persistence.api.WorkspaceAddress;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.util.List;

@RestController
@RequestMapping("/api/internal/remote-workspaces")
public class RemoteWorkspacesController {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteWorkspacesController.class);

    private final RemoteClients.Factory remoteClientsFactory;
    private final WorkspacesManager workspacesManager;

    public RemoteWorkspacesController(
            RemoteClients.Factory remoteClientsFactory,
            WorkspacesManager workspacesManager) {
        this.remoteClientsFactory = remoteClientsFactory;
        this.workspacesManager = workspacesManager;
    }

    @PostMapping("/list")
    public List<RemoteWorkspaceResponse> listRemoteWorkspaces(@RequestBody RemoteWorkspaceConnectionRequest request) {
        validateConnection(request.hostname(), request.port());

        WorkspaceAddress address = new WorkspaceAddress(request.hostname(), request.port(), request.plaintext());
        if (remoteClientsFactory == null) {
            throw Exceptions.invalidRequest("Remote workspace clients are not configured");
        }
        RemoteClients remoteClients = remoteClientsFactory.apply(address);
        try {
            var result = remoteClients.discovery().allWorkspaces().stream()
                    .map(this::toRemoteWorkspaceResponse)
                    .toList();
            LOG.debug("Listed remote workspaces: address={} count={}", address, result.size());
            return result;
        } catch (StatusRuntimeException e) {
            LOG.warn("Failed to connect to remote Jeffrey Server: address={} status={}", address, e.getStatus());
            String message = switch (e.getStatus().getCode()) {
                case UNAVAILABLE -> "Cannot connect to %s:%d. Check that Jeffrey Server is running and the gRPC port (default: 9090) is correct.".formatted(request.hostname(), request.port());
                case DEADLINE_EXCEEDED -> "Connection to %s:%d timed out. The server may be unreachable.".formatted(request.hostname(), request.port());
                case UNKNOWN -> "The endpoint %s:%d does not appear to be a valid Jeffrey Server gRPC service. Make sure you are connecting to the gRPC port, not the HTTP port.".formatted(request.hostname(), request.port());
                case UNIMPLEMENTED -> "The server at %s:%d does not support the required Jeffrey gRPC services. It may be an incompatible version.".formatted(request.hostname(), request.port());
                default -> "Failed to connect to Jeffrey Server at %s:%d: %s".formatted(request.hostname(), request.port(), e.getStatus().getCode());
            };
            throw Exceptions.invalidRequest(message);
        }
    }

    @PostMapping("/create")
    public ResponseEntity<Void> createRemote(@RequestBody RemoteWorkspacesRequest request) {
        LOG.debug("Creating remote workspace");
        validateConnection(request.hostname(), request.port());

        if (request.workspaceIds() == null || request.workspaceIds().isEmpty()) {
            throw Exceptions.invalidRequest("At least one workspace ID is required");
        }

        WorkspaceAddress address = new WorkspaceAddress(request.hostname(), request.port(), request.plaintext());
        for (String workspaceId : request.workspaceIds()) {
            CreateWorkspaceRequest createRequest = CreateWorkspaceRequest.builder()
                    .workspaceId(workspaceId)
                    .address(address)
                    .build();

            workspacesManager.create(createRequest);
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private static void validateConnection(String hostname, int port) {
        if (hostname == null || hostname.isBlank()) {
            throw Exceptions.invalidRequest("Hostname is required");
        }
        if (port < 1 || port > 65535) {
            throw Exceptions.invalidRequest("Port must be between 1 and 65535");
        }
    }

    private RemoteWorkspaceResponse toRemoteWorkspaceResponse(WorkspaceResponse workspaceInfo) {
        return new RemoteWorkspaceResponse(
                workspaceInfo.id(),
                workspaceInfo.name(),
                workspaceInfo.description(),
                workspaceInfo.projectCount());
    }
}
