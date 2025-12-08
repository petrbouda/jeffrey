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

import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.provider.api.repository.WorkspacesRepository;

import java.util.List;
import java.util.Optional;

public class CompositeWorkspacesManager {

    private final WorkspacesRepository workspacesRepository;
    private final SandboxWorkspacesManager sandboxWorkspacesManager;
    private final RemoteWorkspacesManager remoteWorkspacesManager;
    private final LiveWorkspacesManager liveWorkspacesManager;

    public CompositeWorkspacesManager(
            WorkspacesRepository workspacesRepository,
            SandboxWorkspacesManager sandboxWorkspacesManager,
            RemoteWorkspacesManager remoteWorkspacesManager,
            LiveWorkspacesManager liveWorkspacesManager) {

        this.workspacesRepository = workspacesRepository;
        this.sandboxWorkspacesManager = sandboxWorkspacesManager;
        this.remoteWorkspacesManager = remoteWorkspacesManager;
        this.liveWorkspacesManager = liveWorkspacesManager;
    }

    public WorkspaceInfo create(WorkspacesManager.CreateWorkspaceRequest request) {
        return switch (request.type()) {
            case SANDBOX -> sandboxWorkspacesManager.create(request);
            case LIVE -> liveWorkspacesManager.create(request);
            case REMOTE -> remoteWorkspacesManager.create(request);
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

    public WorkspaceManager mapToWorkspaceManager(WorkspaceInfo info) {
        return switch (info.type()) {
            case SANDBOX -> sandboxWorkspacesManager.mapToWorkspaceManager(info);
            case LIVE -> liveWorkspacesManager.mapToWorkspaceManager(info);
            case REMOTE -> remoteWorkspacesManager.mapToWorkspaceManager(info);
        };
    }
}
