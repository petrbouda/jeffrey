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

package pbouda.jeffrey.provider.api.repository;

import pbouda.jeffrey.shared.model.workspace.WorkspaceInfo;

import java.util.List;
import java.util.Optional;

public interface WorkspacesRepository {

    /**
     * Find all workspaces.
     *
     * @return list of all workspaces
     */
    List<WorkspaceInfo> findAll();

    /**
     * Find the workspace by ID.
     *
     * @param workspaceId the workspace ID
     * @return the workspace if it exists, otherwise an empty optional
     */
    Optional<WorkspaceInfo> find(String workspaceId);

    /**
     * Create a new workspace.
     *
     * @param workspaceInfo the workspace to create
     * @return the created workspace
     */
    WorkspaceInfo create(WorkspaceInfo workspaceInfo);

    /**
     * Check if a workspace with the given name already exists.
     *
     * @param name the workspace name
     * @return true if a workspace with this name exists, false otherwise
     */
    boolean existsByName(String name);
}
