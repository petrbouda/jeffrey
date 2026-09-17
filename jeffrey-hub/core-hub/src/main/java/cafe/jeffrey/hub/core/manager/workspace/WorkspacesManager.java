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

package cafe.jeffrey.hub.core.manager.workspace;

import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.model.workspace.WorkspaceReferenceId;

import java.util.List;
import java.util.Optional;

public interface WorkspacesManager {

    /**
     * What a workspace is created from: the reference id the provisioner names it by on the
     * volume, and a display name. It once carried a location and a base location too, which no
     * caller ever set — a workspace's directory is derived from its repository id.
     */
    record CreateWorkspaceRequest(String referenceId, String name) {

        public CreateWorkspaceRequest {
            WorkspaceReferenceId.validate(referenceId);
        }
    }

    /**
     * Create a new workspace.
     *
     * @param request the workspace creation request
     * @return the created workspace
     */
    WorkspaceInfo create(CreateWorkspaceRequest request);

    /**
     * Get all workspaces.
     *
     * @return list of all workspaces
     */
    List<WorkspaceManager> findAll();

    /**
     * Get a workspace by its ID.
     *
     * @param workspaceId the workspace ID
     * @return the workspace if it exists, otherwise an empty optional
     */
    Optional<WorkspaceManager> findById(String workspaceId);

    /**
     * Get a workspace by its reference ID (the user-supplied id the CLI binds to).
     *
     * @param referenceId the workspace reference ID
     * @return the workspace if it exists, otherwise an empty optional
     */
    Optional<WorkspaceManager> findByReferenceId(String referenceId);

}
