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

package cafe.jeffrey.microscope.core.manager.workspace;

import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;

import java.util.Optional;

public interface WorkspaceManager {

    /**
     * Returns the cached workspace snapshot captured when this manager was resolved.
     * Never makes a network call.
     */
    WorkspaceInfo localInfo();

    /**
     * Returns a fresh workspace snapshot from the hub.
     * May involve a network call; falls back to OFFLINE status on failure.
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

}
