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

package cafe.jeffrey.microscope.core.manager.server;

import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.persistence.api.RemoteServerInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Facade for one connected jeffrey-server. Lists workspaces live via gRPC,
 * creates new workspaces on the server, and produces per-workspace managers.
 */
public interface RemoteServerManager {

    @FunctionalInterface
    interface Factory extends Function<RemoteServerInfo, RemoteServerManager> {
    }

    RemoteServerInfo info();

    /**
     * Lists all workspaces on this server (live gRPC ListWorkspaces call).
     */
    List<WorkspaceInfo> workspaces();

    /**
     * Resolves a single workspace on this server to a {@link WorkspaceManager}.
     */
    Optional<WorkspaceManager> workspace(String workspaceId);

    /**
     * Creates a new workspace on this server via the gRPC CreateWorkspace RPC.
     *
     * @param referenceId stable user-supplied id; jeffrey-cli's project.workspace-ref-id
     *                    must match this value to route recordings to the workspace
     * @param name        display name
     */
    WorkspaceInfo createWorkspace(String referenceId, String name);

    /**
     * Removes the local pointer to this server. Does NOT touch server-side data.
     */
    void delete();
}
