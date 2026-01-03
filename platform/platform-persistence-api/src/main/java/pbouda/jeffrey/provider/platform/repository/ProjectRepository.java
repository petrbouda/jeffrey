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

package pbouda.jeffrey.provider.platform.repository;

import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.ProjectInfo;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository {

    /**
     * Delete the project.
     */
    void delete();

    /**
     * Find all profiles belonging to the given project.
     *
     * @return list of profiles.
     */
    List<ProfileInfo> findAllProfiles();

    /**
     * Find the project information.
     *
     * @return project information.
     */
    Optional<ProjectInfo> find();

    /**
     * Update the project name.
     *
     * @param name new project's name.
     */
    void updateProjectName(String name);
}
