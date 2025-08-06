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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.model.Workspace;

import java.util.List;
import java.util.Optional;

public interface WorkspaceManager {

    /**
     * Create a new workspace.
     *
     * @param id the workspace ID
     * @param name the workspace name
     * @param description the workspace description (optional)
     * @param path the workspace path (optional)
     * @return the created workspace
     */
    Workspace create(String id, String name, String description, String path);

    /**
     * Get all workspaces.
     *
     * @return list of all workspaces
     */
    List<Workspace> all();


    /**
     * Get a workspace by its ID.
     *
     * @param workspaceId the workspace ID
     * @return the workspace if it exists, otherwise an empty optional
     */
    Optional<Workspace> workspace(String workspaceId);

    /**
     * Delete a workspace by its ID.
     *
     * @param workspaceId the workspace ID
     * @return true if the workspace was deleted, false if it didn't exist
     */
    boolean delete(String workspaceId);
}