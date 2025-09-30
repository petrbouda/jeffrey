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
import pbouda.jeffrey.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.repository.RemoteWorkspaceRepository;

import java.util.List;
import java.util.function.Function;

public interface WorkspaceManager {

    @FunctionalInterface
    interface Factory extends Function<WorkspaceInfo, WorkspaceManager> {
    }

    /**
     * Returns the workspace information associated with this manager.
     *
     * @return the workspace information
     */
    WorkspaceInfo resolveInfo();

    /**
     * Find all projects in the workspace.
     *
     * @return list of projects in the workspace.
     */
    List<? extends ProjectManager> findAllProjects();

    /**
     * Deletes the workspace from the repository.
     */
    void delete();

    /**
     * Returns the type of the workspace (e.g., LOCAL, REMOTE).
     *
     * @return the workspace type
     */
    WorkspaceType type();

    /**
     * Creates and returns a remote workspace repository instance for this workspace.
     *
     * @return the remote workspace repository
     */
    RemoteWorkspaceRepository remoteWorkspaceRepository();

    /**
     * Returns the workspace event manager associated with this workspace.
     * Contains methods for managing workspace events and sessions.
     *
     * @return the workspace event manager
     */
    WorkspaceEventManager workspaceEventManager();
}
