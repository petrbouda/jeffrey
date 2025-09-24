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

package pbouda.jeffrey.manager.project;

import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceSessionInfo;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface ProjectSessionManager {

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, ProjectSessionManager> {
    }

    /**
     * Create a new workspace session.
     *
     * @param workspaceSessionInfo the workspace session to create
     */
    void createSession(WorkspaceSessionInfo workspaceSessionInfo);

    /**
     * Find all workspace sessions belonging to the given project.
     *
     * @return list of workspace sessions
     */
    List<WorkspaceSessionInfo> findAllSessions();

    /**
     * Find a workspace session by its ID.
     *
     * @param sessionId the ID of the session to find
     * @return an Optional containing the found session, or empty if not found
     */
    Optional<WorkspaceSessionInfo> findSessionById(String sessionId);
}
