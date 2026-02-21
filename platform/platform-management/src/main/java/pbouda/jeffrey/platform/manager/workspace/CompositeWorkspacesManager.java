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

package pbouda.jeffrey.platform.manager.workspace;

import org.springframework.beans.factory.ObjectFactory;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.provider.platform.repository.WorkspacesRepository;

import java.util.List;
import java.util.Optional;

public final class CompositeWorkspacesManager implements WorkspacesManager {

    private final WorkspacesRepository workspacesRepository;
    private final ObjectFactory<SandboxWorkspacesManager> sandboxWorkspacesManager;
    private final ObjectFactory<RemoteWorkspacesManager> remoteWorkspacesManager;
    private final ObjectFactory<LiveWorkspacesManager> liveWorkspacesManager;

    public CompositeWorkspacesManager(
            WorkspacesRepository workspacesRepository,
            ObjectFactory<SandboxWorkspacesManager> sandboxWorkspacesManager,
            ObjectFactory<RemoteWorkspacesManager> remoteWorkspacesManager,
            ObjectFactory<LiveWorkspacesManager> liveWorkspacesManager) {

        this.workspacesRepository = workspacesRepository;
        this.sandboxWorkspacesManager = sandboxWorkspacesManager;
        this.remoteWorkspacesManager = remoteWorkspacesManager;
        this.liveWorkspacesManager = liveWorkspacesManager;
    }

    public WorkspaceInfo create(WorkspacesManager.CreateWorkspaceRequest request) {
        return switch (request.type()) {
            case SANDBOX -> sandboxWorkspacesManager.getObject().create(request);
            case LIVE -> liveWorkspacesManager.getObject().create(request);
            case REMOTE -> remoteWorkspacesManager.getObject().create(request);
        };
    }

    public List<? extends WorkspaceManager> findAll() {
        return workspacesRepository.findAll().stream()
                .map(this::mapToWorkspaceManager)
                .toList();
    }

    public Optional<WorkspaceManager> findById(String workspaceId) {
        return workspacesRepository.findAll().stream()
                .filter(info -> info.id().equals(workspaceId))
                .findFirst()
                .map(this::mapToWorkspaceManager);
    }

    public Optional<WorkspaceManager> findByOriginId(String originId) {
        return workspacesRepository.findByOriginId(originId)
                .map(this::mapToWorkspaceManager);
    }

    public WorkspaceManager mapToWorkspaceManager(WorkspaceInfo info) {
        return switch (info.type()) {
            case SANDBOX -> sandboxWorkspacesManager.getObject().mapToWorkspaceManager(info);
            case LIVE -> liveWorkspacesManager.getObject().mapToWorkspaceManager(info);
            case REMOTE -> remoteWorkspacesManager.getObject().mapToWorkspaceManager(info);
        };
    }
}
