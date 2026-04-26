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

package cafe.jeffrey.local.core.manager.project;

import cafe.jeffrey.local.persistence.model.RemoteWorkspaceInfo;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface ProjectsManager {

    @FunctionalInterface
    interface Factory extends Function<RemoteWorkspaceInfo, ProjectsManager> {
    }

    List<ProjectManager> findAll();

    List<ProjectManager> findAllIncludingDeleted();

    Optional<ProjectManager> project(String projectId);

    /**
     * Find all distinct namespaces across all projects.
     * Used by UI to provide namespace filtering options.
     *
     * @return list of distinct namespace names (excluding null values)
     */
    List<String> findAllNamespaces();

}
