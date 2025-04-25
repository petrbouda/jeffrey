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

import pbouda.jeffrey.common.model.ExternalProjectLink;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.provider.api.repository.model.CreateProject;

import java.util.List;

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
     * Create a new external project link with an existing project ID.
     *
     * @param externalProjectLink external project link.
     * @return saved an external project link.
     */
    ExternalProjectLink createExternalProjectLink(ExternalProjectLink externalProjectLink);

    /**
     * Retrieves external project links by an external component ID.
     *
     * @return list of external project links.
     */
    List<ExternalProjectLink> findExternalProjectLinks(String externalComponentId);
}
