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

package pbouda.jeffrey.platform.manager.workspace;

import jakarta.ws.rs.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteClients;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteDiscoveryClient;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteDiscoveryClient.WorkspaceResult;
import pbouda.jeffrey.provider.platform.repository.WorkspacesRepository;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class RemoteWorkspacesManager implements WorkspacesManager {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteWorkspacesManager.class);

    public interface Factory extends Function<URI, RemoteWorkspacesManager> {
    }

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
    public List<? extends WorkspaceManager> findAll() {
        return workspacesRepository.findAll().stream()
                .filter(WorkspaceInfo::isRemote)
                .map(workspaceManagerFactory)
                .toList();
    }

    @Override
    public Optional<WorkspaceManager> findById(String workspaceId) {
        return workspacesRepository.find(workspaceId)
                .filter(WorkspaceInfo::isRemote)
                .map(workspaceManagerFactory);
    }

    @Override
    public Optional<WorkspaceManager> findByOriginId(String originId) {
        return workspacesRepository.findByOriginId(originId)
                .filter(WorkspaceInfo::isRemote)
                .map(workspaceManagerFactory);
    }

    @Override
    public WorkspaceManager mapToWorkspaceManager(WorkspaceInfo info) {
        return workspaceManagerFactory.apply(info);
    }

    @Override
    public WorkspaceInfo create(CreateWorkspaceRequest request) {
        LOG.debug("Creating remote workspace: name={}", request.name());
        RemoteClients remoteClients =
                remoteClientsFactory.apply(request.baseLocation().toUri());

        WorkspaceResult result = remoteClients.discovery().workspace(request.workspaceId());
        return switch (result.status()) {
            case AVAILABLE -> workspacesRepository.create(result.info());
            case UNAVAILABLE -> throw new NotFoundException("Remote workspace not found");
            case OFFLINE -> throw new IllegalStateException("Remote workspace is unreachable");
            case UNKNOWN -> throw new IllegalStateException("Unknown remote workspace status");
        };
    }
}
