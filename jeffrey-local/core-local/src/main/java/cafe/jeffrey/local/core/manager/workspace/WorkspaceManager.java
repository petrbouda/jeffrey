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

package cafe.jeffrey.local.core.manager.workspace;

import cafe.jeffrey.local.core.client.RemoteProfilerClient;
import cafe.jeffrey.local.core.resources.response.WorkspaceEventResponse;
import cafe.jeffrey.local.persistence.api.RemoteWorkspaceInfo;
import cafe.jeffrey.local.core.manager.project.ProjectsManager;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface WorkspaceManager {

    @FunctionalInterface
    interface Factory extends Function<RemoteWorkspaceInfo, WorkspaceManager> {
    }

    /**
     * Returns the locally stored workspace information without remote resolution.
     * Unlike {@link #resolveInfo()}, this never makes network calls.
     *
     * @return the locally stored workspace information
     */
    RemoteWorkspaceInfo localInfo();

    /**
     * Returns the workspace information associated with this manager.
     * For remote workspaces, this may involve network calls to check availability.
     *
     * @return the workspace information
     */
    RemoteWorkspaceInfo resolveInfo();

    /**
     * Returns the projects manager for managing multiple projects within the workspace.
     *
     * @return the projects manager
     */
    ProjectsManager projectsManager();

    /**
     * Returns the remote profiler client for this workspace's server, if available.
     * Only remote workspaces have a profiler client.
     */
    default Optional<RemoteProfilerClient> profilerClient() {
        return Optional.empty();
    }

    /**
     * Returns workspace events for this workspace.
     * Only remote workspaces have events; local workspaces return an empty list.
     *
     * @return list of workspace event responses
     */
    default List<WorkspaceEventResponse> events() {
        return List.of();
    }

    /**
     * Deletes the workspace from the repository.
     */
    void delete();

}
