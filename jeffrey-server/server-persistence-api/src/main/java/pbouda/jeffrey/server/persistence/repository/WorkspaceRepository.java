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

package pbouda.jeffrey.server.persistence.repository;

import pbouda.jeffrey.shared.common.model.ProjectInfo;

import java.util.List;

public interface WorkspaceRepository {

    /**
     * Block the workspace — stops all event processing and periodic jobs.
     */
    void block();

    /**
     * Unblock the workspace — resumes event processing and periodic jobs.
     */
    void unblock();

    /**
     * Delete the workspace row and all workspace-scoped data from the database.
     */
    void delete();

    /**
     * Update the streaming enabled flag for this workspace.
     *
     * @param enabled {@code true} to force-enable, {@code false} to force-disable,
     *                {@code null} to inherit from global setting.
     */
    void updateStreamingEnabled(Boolean enabled);

    /**
     * Find all projects in the workspace.
     *
     * @return list of projects in the workspace.
     */
    List<ProjectInfo> findAllProjects();
}
