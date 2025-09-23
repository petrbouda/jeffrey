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

import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.manager.workspace.WorkspacesManager;

import java.net.URI;
import java.util.List;

public class MirroringWorkspacesManagerImpl implements MirroringWorkspacesManager {

    private final WorkspacesManager workspacesManager;
    private final MirroringWorkspaceClient mirroringWorkspaceClient;

    public MirroringWorkspacesManagerImpl(
            WorkspacesManager workspacesManager,
            MirroringWorkspaceClient mirroringWorkspaceClient) {

        this.workspacesManager = workspacesManager;
        this.mirroringWorkspaceClient = mirroringWorkspaceClient;
    }

    @Override
    public List<? extends MirroringWorkspaceManager> findAll() {
        return mirroringWorkspaceClient.allMirroringWorkspaces();
    }

    @Override
    public WorkspaceInfo create(String id, URI remoteUri) {
        MirroringWorkspaceManager workspaceManager = mirroringWorkspaceClient.mirroringWorkspace(id);
        WorkspaceInfo workspaceInfo = workspaceManager.info();
        return workspacesManager.create(
                workspaceInfo.id(),
                workspaceInfo.name(),
                workspaceInfo.description(),
                workspaceInfo.location().toString(),
                true);
    }
}
