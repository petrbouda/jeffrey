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

package pbouda.jeffrey.platform.manager.workspace;

import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.repository.RemoteWorkspaceRepository;

import java.util.function.Function;

public interface WorkspaceManager {

    @FunctionalInterface
    interface Factory extends Function<WorkspaceInfo, WorkspaceManager> {
    }

    /**
     * Returns the locally stored workspace information without remote resolution.
     * Unlike {@link #resolveInfo()}, this never makes network calls.
     *
     * @return the locally stored workspace information
     */
    WorkspaceInfo localInfo();

    /**
     * Returns the workspace information associated with this manager.
     * For remote workspaces, this may involve network calls to check availability.
     *
     * @return the workspace information
     */
    WorkspaceInfo resolveInfo();

    /**
     * Returns the projects manager for managing multiple projects within the workspace.
     *
     * @return the projects manager
     */
    ProjectsManager projectsManager();

    /**
     * Deletes the workspace from the repository.
     */
    void delete();

    /**
     * Returns the type of the workspace (e.g., LIVE, REMOTE).
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
}
