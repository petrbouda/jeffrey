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

package pbouda.jeffrey.manager.workspace.mirror;

import jakarta.ws.rs.NotFoundException;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.manager.workspace.WorkspacesManager;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class MirroringWorkspacesManager implements WorkspacesManager {

    public interface Factory extends Function<URI, MirroringWorkspacesManager> {
    }

    private final WorkspacesManager workspacesManager;
    private final MirroringWorkspaceClient mirroringWorkspaceClient;

    public MirroringWorkspacesManager(
            WorkspacesManager workspacesManager,
            MirroringWorkspaceClient mirroringWorkspaceClient) {

        this.workspacesManager = workspacesManager;
        this.mirroringWorkspaceClient = mirroringWorkspaceClient;
    }

    @Override
    public List<? extends WorkspaceManager> findAll() {
        return mirroringWorkspaceClient.allMirroringWorkspaces().stream()
                .map(info -> new MirroringWorkspaceManager(info, mirroringWorkspaceClient))
                .toList();
    }

    @Override
    public Optional<WorkspaceManager> workspace(String workspaceId) {
        return Optional.empty();
    }

    @Override
    public Optional<WorkspaceManager> workspaceByRepositoryId(String workspaceRepositoryId) {
        return Optional.empty();
    }

    @Override
    public WorkspaceInfo create(CreateWorkspaceRequest request) {
        WorkspaceManager workspaceManager = mirroringWorkspaceClient.mirroringWorkspace(request.workspaceId())
                .map(info -> new MirroringWorkspaceManager(info, mirroringWorkspaceClient))
                .orElseThrow(() -> new NotFoundException("No mirroring workspace with ID '" + request.workspaceId() + "' found"));

        WorkspaceInfo workspaceInfo = workspaceManager.info();

        CreateWorkspaceRequest createRequest = CreateWorkspaceRequest.builder()
                .workspaceSourceId(workspaceInfo.id())
                .name(workspaceInfo.name())
                .description(workspaceInfo.description())
                .location(workspaceInfo.location())
                .baseLocation(workspaceInfo.baseLocation())
                .isMirror(true)
                .build();

        return workspacesManager.create(createRequest);
    }
}
