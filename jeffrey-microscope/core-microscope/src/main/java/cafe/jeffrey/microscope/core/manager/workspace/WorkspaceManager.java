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

import cafe.jeffrey.microscope.core.client.RemoteProfilerClient;
import cafe.jeffrey.microscope.core.resources.response.WorkspaceEventsResponse;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.util.List;
import java.util.Optional;

public interface WorkspaceManager {

    /**
     * Returns the cached workspace snapshot captured when this manager was resolved.
     * Never makes a network call.
     */
    WorkspaceInfo localInfo();

    /**
     * Returns a fresh workspace snapshot from the remote server.
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
     * Returns the remote profiler client for this workspace's server, if available.
     * Only remote workspaces have a profiler client.
     */
    default Optional<RemoteProfilerClient> profilerClient() {
        return Optional.empty();
    }

    /**
     * Returns the latest workspace events for this workspace, capped at {@code limit},
     * along with the unfiltered total count. Only remote workspaces produce events;
     * local workspaces return an empty payload.
     *
     * @param limit maximum number of events to return
     */
    default WorkspaceEventsResponse events(int limit) {
        return new WorkspaceEventsResponse(List.of(), 0L, limit);
    }

    /**
     * Upserts workspace-level profiler settings. Applies the given agent
     * settings string to every project in this workspace that doesn't
     * override at the project level.
     */
    void upsertProfilerSettings(String agentSettings);

    /**
     * Returns the workspace-level and global-level profiler settings for this
     * workspace. Either field may be {@code null} if no row exists at that
     * level. The caller decides which is "effectively in force" — workspace
     * overrides global.
     */
    RemoteProfilerClient.WorkspaceProfilerLevels fetchEffectiveProfilerSettings();

    /**
     * Removes the workspace-level profiler settings, causing the workspace
     * to fall back to the global default.
     */
    void deleteProfilerSettings();

    /**
     * Deletes the workspace from the repository.
     */
    void delete();

}
