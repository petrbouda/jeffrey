/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.persistence.api;

import cafe.jeffrey.hub.model.ProjectInfo;

import java.util.List;
import java.util.Optional;

public interface ProjectsRepository {

    /**
     * Create a new project.
     *
     * @param project project information.
     * @return newly create ProjectInfo
     */
    ProjectInfo create(ProjectInfo project);

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
