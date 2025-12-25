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

package pbouda.jeffrey.manager.project;

import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.profile.manager.model.CreateProject;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface ProjectsManager {

    @FunctionalInterface
    interface Factory extends Function<WorkspaceInfo, ProjectsManager> {
    }

    ProjectManager create(CreateProject createProject);

    List<ProjectManager> findAll();

    Optional<ProjectManager> project(String projectId);

    /**
     * Find a project by its origin project ID.
     *
     * @param originProjectId the origin project ID from workspace
     * @return the project if it exists, otherwise an empty optional
     */
    Optional<ProjectManager> findByOriginProjectId(String originProjectId);

}
