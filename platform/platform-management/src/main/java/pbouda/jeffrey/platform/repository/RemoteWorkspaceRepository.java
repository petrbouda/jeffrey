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

package pbouda.jeffrey.platform.repository;

import pbouda.jeffrey.shared.common.model.repository.RemoteProject;
import pbouda.jeffrey.shared.common.model.repository.RemoteProjectInstance;
import pbouda.jeffrey.shared.common.model.repository.RemoteProjectInstanceSession;
import pbouda.jeffrey.shared.common.model.repository.RemoteWorkspaceSettings;

import java.util.List;

public interface RemoteWorkspaceRepository {

    /**
     * Returns a list of all projects from the remote workspace.
     *
     * @return list of all projects
     */
    List<RemoteProject> allProjects();

    /**
     * Returns a list of all instances belonging to a single project.
     * Instances represent deployed PODs/containers that create sessions.
     *
     * @param project the project to get instances for
     * @return list of all instances in a single project
     */
    List<RemoteProjectInstance> allInstances(RemoteProject project);

    /**
     * Returns a list of all sessions belonging to a single project.
     * Sessions may be organized under instances or directly under the project.
     *
     * @param project the project to get sessions for
     * @return list of all sessions in a single project
     */
    List<RemoteProjectInstanceSession> allSessions(RemoteProject project);

    /**
     * Uploads profiler settings for the workspace.
     *
     * @param settings the workspace profiler's settings
     */
    void uploadSettings(RemoteWorkspaceSettings settings);

    /**
     * Removes legacy settings from the remote workspace.
     *
     * @param keepMaxVersions the maximum number of versions to keep
     */
    void removeLegacySettings(int keepMaxVersions);
}
