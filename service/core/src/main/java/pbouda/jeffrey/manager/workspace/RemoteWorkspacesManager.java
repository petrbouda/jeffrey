/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.manager.workspace;

import jakarta.ws.rs.NotFoundException;
import pbouda.jeffrey.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.manager.project.ProjectsManager;
import pbouda.jeffrey.manager.workspace.remote.RemoteWorkspaceClient;
import pbouda.jeffrey.manager.workspace.remote.RemoteWorkspaceManager;
import pbouda.jeffrey.provider.api.repository.WorkspacesRepository;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class RemoteWorkspacesManager implements WorkspacesManager {

    public interface Factory extends Function<URI, RemoteWorkspacesManager> {
    }

    private final JeffreyDirs jeffreyDirs;
    private final WorkspacesRepository workspacesRepository;
    private final RemoteWorkspaceClient.Factory remoteWorkspaceClientFactory;
    private final ProjectsManager.Factory commonProjectsManagerFactory;

    public RemoteWorkspacesManager(
            JeffreyDirs jeffreyDirs,
            WorkspacesRepository workspacesRepository,
            RemoteWorkspaceClient.Factory remoteWorkspaceClientFactory,
            ProjectsManager.Factory commonProjectsManagerFactory) {

        this.jeffreyDirs = jeffreyDirs;
        this.workspacesRepository = workspacesRepository;
        this.remoteWorkspaceClientFactory = remoteWorkspaceClientFactory;
        this.commonProjectsManagerFactory = commonProjectsManagerFactory;
    }

    private WorkspaceManager toWorkspaceManager(WorkspaceInfo workspaceInfo) {
        URI baseUri = workspaceInfo.baseLocation().toUri();
        return new RemoteWorkspaceManager(
                jeffreyDirs,
                workspaceInfo,
                remoteWorkspaceClientFactory.apply(baseUri),
                commonProjectsManagerFactory);
    }

    @Override
    public List<? extends WorkspaceManager> findAll() {
        return workspacesRepository.findAll().stream()
                .filter(WorkspaceInfo::isRemote)
                .map(this::toWorkspaceManager)
                .toList();
    }

    @Override
    public Optional<WorkspaceManager> findById(String workspaceId) {
        return workspacesRepository.find(workspaceId)
                .filter(WorkspaceInfo::isRemote)
                .map(this::toWorkspaceManager);
    }

    @Override
    public WorkspaceManager mapToWorkspaceManager(WorkspaceInfo info) {
        return toWorkspaceManager(info);
    }

    @Override
    public WorkspaceInfo create(CreateWorkspaceRequest request) {
        RemoteWorkspaceClient remoteWorkspaceClient =
                remoteWorkspaceClientFactory.apply(request.baseLocation().toUri());

        RemoteWorkspaceClient.WorkspaceResult result = remoteWorkspaceClient.workspace(request.workspaceId());
        return switch (result.status()) {
            case AVAILABLE -> workspacesRepository.create(result.info());
            case UNAVAILABLE -> throw new NotFoundException("Remote workspace not found");
            case OFFLINE -> throw new IllegalStateException("Remote workspace is unreachable");
            case UNKNOWN -> throw new IllegalStateException("Unknown remote workspace status");
        };
    }
}
