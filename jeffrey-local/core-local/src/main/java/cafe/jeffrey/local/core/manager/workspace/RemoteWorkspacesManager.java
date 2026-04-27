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

package cafe.jeffrey.local.core.manager.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.local.core.client.RemoteClients;
import cafe.jeffrey.local.core.client.RemoteDiscoveryClient.WorkspaceResult;
import cafe.jeffrey.local.persistence.api.WorkspacesRepository;
import cafe.jeffrey.local.persistence.api.RemoteWorkspaceInfo;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.util.List;
import java.util.Optional;

public final class RemoteWorkspacesManager implements WorkspacesManager {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteWorkspacesManager.class);

    private final WorkspacesRepository workspacesRepository;
    private final WorkspaceManager.Factory workspaceManagerFactory;
    private final RemoteClients.Factory remoteClientsFactory;

    public RemoteWorkspacesManager(
            WorkspacesRepository workspacesRepository,
            WorkspaceManager.Factory workspaceManagerFactory,
            RemoteClients.Factory remoteClientsFactory) {

        this.workspacesRepository = workspacesRepository;
        this.workspaceManagerFactory = workspaceManagerFactory;
        this.remoteClientsFactory = remoteClientsFactory;
    }

    @Override
    public RemoteWorkspaceInfo create(CreateWorkspaceRequest request) {
        LOG.debug("Creating remote workspace: name={}", request.name());
        RemoteClients remoteClients =
                remoteClientsFactory.apply(request.address());

        WorkspaceResult result = remoteClients.discovery().workspace(request.workspaceId());
        return switch (result.status()) {
            case AVAILABLE -> workspacesRepository.create(result.info());
            case UNAVAILABLE -> throw Exceptions.workspaceNotFound(request.workspaceId());
            case OFFLINE -> throw new IllegalStateException("Remote workspace is unreachable");
            case UNKNOWN -> throw new IllegalStateException("Unknown remote workspace status");
        };
    }

    @Override
    public List<? extends WorkspaceManager> findAll() {
        return workspacesRepository.findAll().stream()
                .map(workspaceManagerFactory)
                .toList();
    }

    @Override
    public Optional<WorkspaceManager> findById(String workspaceId) {
        return workspacesRepository.find(workspaceId)
                .map(workspaceManagerFactory);
    }

}
