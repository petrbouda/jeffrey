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

package cafe.jeffrey.microscope.core.manager.project;

import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface ProjectsManager {

    @FunctionalInterface
    interface Factory extends Function<WorkspaceInfo, ProjectsManager> {
    }

    List<ProjectManager> findAll();

    /**
     * Lists projects without converting a remote failure into an empty UI result.
     */
    default List<ProjectManager> findAllOrThrow() {
        return findAll();
    }

    List<ProjectManager> findAllIncludingDeleted();

    Optional<ProjectManager> project(String projectId);

    /**
     * Finds a project without converting a remote failure into a missing project.
     */
    default Optional<ProjectManager> projectOrThrow(String projectId) {
        return project(projectId);
    }

    /**
     * Find all distinct namespaces across all projects.
     * Used by UI to provide namespace filtering options.
     *
     * @return list of distinct namespace names (excluding null values)
     */
    List<String> findAllNamespaces();

}
