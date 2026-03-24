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

package pbouda.jeffrey.local.core.resources;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.local.core.manager.workspace.WorkspacesManager.CreateWorkspaceRequest;
import pbouda.jeffrey.local.core.client.RemoteClients;
import pbouda.jeffrey.local.core.resources.request.RemoteWorkspaceConnectionRequest;
import pbouda.jeffrey.local.core.resources.request.RemoteWorkspacesRequest;
import pbouda.jeffrey.local.core.resources.response.RemoteWorkspaceResponse;
import pbouda.jeffrey.local.persistence.model.WorkspaceAddress;
import pbouda.jeffrey.shared.common.exception.Exceptions;

import java.util.List;

public class RemoteWorkspacesResource {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteWorkspacesResource.class);

    private final RemoteClients.Factory remoteClientsFactory;
    private final WorkspacesManager workspacesManager;

    public RemoteWorkspacesResource(
            RemoteClients.Factory remoteClientsFactory,
            WorkspacesManager workspacesManager) {

        this.remoteClientsFactory = remoteClientsFactory;
        this.workspacesManager = workspacesManager;
    }

    @POST
    @Path("/list")
    public List<RemoteWorkspaceResponse> listRemoteWorkspaces(RemoteWorkspaceConnectionRequest request) {
        validateConnection(request.hostname(), request.port());

        WorkspaceAddress address = new WorkspaceAddress(request.hostname(), request.port());
        RemoteClients remoteClients = remoteClientsFactory.apply(address);
        var result = remoteClients.discovery().allWorkspaces().stream()
                .map(this::toRemoteWorkspaceResponse)
                .toList();
        LOG.debug("Listed remote workspaces: address={} count={}", address, result.size());
        return result;
    }

    @POST
    @Path("/create")
    public Response createRemote(RemoteWorkspacesRequest request) {
        LOG.debug("Creating remote workspace");
        validateConnection(request.hostname(), request.port());

        if (request.workspaceIds() == null || request.workspaceIds().isEmpty()) {
            throw Exceptions.invalidRequest("At least one workspace ID is required");
        }

        WorkspaceAddress address = new WorkspaceAddress(request.hostname(), request.port());
        for (String workspaceId : request.workspaceIds()) {
            CreateWorkspaceRequest createRequest = CreateWorkspaceRequest.builder()
                    .workspaceId(workspaceId)
                    .address(address)
                    .build();

            workspacesManager.create(createRequest);
        }
        return Response.status(Response.Status.CREATED).build();
    }

    private static void validateConnection(String hostname, int port) {
        if (hostname == null || hostname.isBlank()) {
            throw Exceptions.invalidRequest("Hostname is required");
        }
        if (port < 1 || port > 65535) {
            throw Exceptions.invalidRequest("Port must be between 1 and 65535");
        }
    }

    private RemoteWorkspaceResponse toRemoteWorkspaceResponse(pbouda.jeffrey.local.core.resources.response.WorkspaceResponse workspaceInfo) {
        return new RemoteWorkspaceResponse(
                workspaceInfo.id(),
                workspaceInfo.name(),
                workspaceInfo.description(),
                workspaceInfo.projectCount()
        );
    }
}
