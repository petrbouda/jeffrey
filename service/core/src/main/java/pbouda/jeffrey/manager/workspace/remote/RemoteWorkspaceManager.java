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

package pbouda.jeffrey.manager.workspace.remote;

import jakarta.ws.rs.NotFoundException;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.manager.workspace.WorkspaceEventManager;
import pbouda.jeffrey.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.repository.RemoteWorkspaceRepository;

import java.util.List;

public class RemoteWorkspaceManager implements WorkspaceManager {

    private final WorkspaceInfo workspaceInfo;
    private final RemoteWorkspaceClient remoteWorkspaceClient;

    public RemoteWorkspaceManager(WorkspaceInfo workspaceInfo, RemoteWorkspaceClient remoteWorkspaceClient) {
        this.workspaceInfo = workspaceInfo;
        this.remoteWorkspaceClient = remoteWorkspaceClient;
    }

    @Override
    public WorkspaceInfo resolveInfo() {
        RemoteWorkspaceClient.WorkspaceResult result = remoteWorkspaceClient.workspace(workspaceInfo.id());
        return switch (result.status()) {
            case AVAILABLE -> result.info();
            case UNAVAILABLE -> throw new NotFoundException("Remote workspace not found");
            case OFFLINE -> throw new IllegalStateException("Remote workspace is unreachable");
            case UNKNOWN -> throw new IllegalStateException("Unknown remote workspace status");
        };
    }

    @Override
    public List<? extends ProjectManager> findAllProjects() {
//        mirroringWorkspaceClient.allProjects(workspaceInfo.id()).stream()
//                .map(projectResponse -> new MirroringProjectManager(projectResponse, this))
//                .toList();
        return List.of();
    }

    @Override
    public void delete() {
        throw new UnsupportedOperationException("Mirroring workspace cannot be deleted.");
    }

    @Override
    public WorkspaceType type() {
        return WorkspaceType.SANDBOX;
    }

    @Override
    public RemoteWorkspaceRepository remoteWorkspaceRepository() {
        throw new UnsupportedOperationException("Mirroring workspace does not support remote repository.");
    }

    @Override
    public WorkspaceEventManager workspaceEventManager() {
        throw new UnsupportedOperationException("Mirroring workspace does not support workspace events.");
    }
}
