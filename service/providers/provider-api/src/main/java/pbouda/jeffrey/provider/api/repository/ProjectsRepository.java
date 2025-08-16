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

import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.provider.api.repository.model.CreateProject;

import java.util.List;
import java.util.Optional;

public interface ProjectsRepository {

    /**
     * Create a new project.
     *
     * @param project project information.
     * @return newly create ProjectInfo
     */
    ProjectInfo create(CreateProject project);

    /**
     * Find all projects.
     *
     * @return list of projects.
     */
    List<ProjectInfo> findAllProjects();

    /**
     * Find all projects by workspace ID.
     *
     * @param workspaceId workspace ID to filter by, or null for projects without workspace
     * @return list of projects in the specified workspace.
     */
    List<ProjectInfo> findAllProjects(String workspaceId);

    /**
     * Find a project by its origin project ID.
     *
     * @param originProjectId the origin project ID to search for
     * @return project information if found, empty otherwise
     */
    Optional<ProjectInfo> findByOriginProjectId(String originProjectId);
}
