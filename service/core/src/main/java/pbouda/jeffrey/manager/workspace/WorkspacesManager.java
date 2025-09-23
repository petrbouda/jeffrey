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

import java.util.List;
import java.util.Optional;

public interface WorkspacesManager {

    /**
     * Create a new workspace.
     *
     * @param workspaceSourceId the workspace source ID
     * @param name              the workspace name
     * @param description       the workspace description (optional)
     * @param location          the workspace path (optional)
     * @param isMirror          whether the workspace is a mirror
     * @return the created workspace
     */
    WorkspaceInfo create(String workspaceSourceId, String name, String description, String location, boolean isMirror);

    /**
     * Get all workspaces.
     *
     * @return list of all workspaces
     */
    List<? extends WorkspaceManager> findAll(boolean excludeMirrored);

    /**
     * Get a workspace by its ID.
     *
     * @param workspaceId the workspace ID
     * @return the workspace if it exists, otherwise an empty optional
     */
    Optional<WorkspaceManager> workspace(String workspaceId);

    /**
     * Get a workspace by its Workspace Repository ID. Repository ID is the identifier to correlate workspaces
     * in Jeffrey and in the repository.
     *
     * @param workspaceRepositoryId the Workspace Repository ID
     * @return the workspace if it exists, otherwise an empty optional
     */
    Optional<WorkspaceManager> workspaceByRepositoryId(String workspaceRepositoryId);
}
