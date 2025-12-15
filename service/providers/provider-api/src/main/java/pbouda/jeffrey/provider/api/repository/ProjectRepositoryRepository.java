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


import pbouda.jeffrey.common.model.workspace.WorkspaceSessionInfo;
import pbouda.jeffrey.provider.api.model.DBRepositoryInfo;

import java.util.List;
import java.util.Optional;

public interface ProjectRepositoryRepository {

    void insert(DBRepositoryInfo repositoryInfo);

    List<DBRepositoryInfo> getAll();

    void delete(String id);

    void deleteAll();

    // Workspace Sessions Methods

    /**
     * Create a new workspace session.
     *
     * @param workspaceSessionInfo the workspace session to create
     */
    void createSession(WorkspaceSessionInfo workspaceSessionInfo);

    /**
     * Delete a workspace session by its session ID.
     *
     * @param sessionId the session ID to delete
     */
    void deleteSession(String sessionId);

    /**
     * Find all workspace sessions for a given project ID.
     *
     * @return list of workspace sessions for the project
     */
    List<WorkspaceSessionInfo> findAllSessions();

    /**
     * Find a single workspace session by project ID and session ID.
     *
     * @param sessionId the session ID
     * @return the workspace session if it exists, otherwise an empty optional
     */
    Optional<WorkspaceSessionInfo> findSessionById(String sessionId);
}
