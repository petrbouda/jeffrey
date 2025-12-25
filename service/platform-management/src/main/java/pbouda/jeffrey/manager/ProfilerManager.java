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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.model.ProfilerInfo;

import java.util.List;
import java.util.Optional;

public interface ProfilerManager {

    /**
     * Inserts profiler settings related to the configuration of the agent.
     *
     * @param profiler the profiler information to be inserted
     */
    void upsertSettings(ProfilerInfo profiler);

    /**
     * Retrieves the profiler information.
     *
     * @return the profiler information
     */
    Optional<ProfilerInfo> findSettings(String workspaceId, String projectId);

    /**
     * Retrieves all profiler settings.
     *
     * @return a list of all profiler settings
     */
    List<ProfilerInfo> findAllSettings();

    /**
     * Deletes profiler settings for the specified workspace and project.
     *
     * @param workspaceId the workspace ID
     * @param projectId   the project ID
     */
    void deleteSettings(String workspaceId, String projectId);
}
