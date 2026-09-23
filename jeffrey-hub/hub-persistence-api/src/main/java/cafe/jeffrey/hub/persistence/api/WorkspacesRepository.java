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

import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;

import java.util.List;
import java.util.Optional;

public interface WorkspacesRepository {

    /**
     * Find all workspaces.
     *
     * @return list of all workspaces
     */
    List<WorkspaceInfo> findAll();

    /**
     * Find the workspace by ID.
     *
     * @param workspaceId the workspace ID
     * @return the workspace if it exists, otherwise an empty optional
     */
    Optional<WorkspaceInfo> find(String workspaceId);

    /**
     * Find the workspace by its origin ID.
     *
     * @param referenceId the workspace origin ID
     * @return the workspace if it exists, otherwise an empty optional
     */
    Optional<WorkspaceInfo> findByReferenceId(String referenceId);

    /**
     * Create a new workspace.
     *
     * @param workspaceInfo the workspace to create
     * @return the created workspace
     */
    WorkspaceInfo create(WorkspaceInfo workspaceInfo);

    /**
     * Check if a workspace with the given name already exists.
     *
     * @param name the workspace name
     * @return true if a workspace with this name exists, false otherwise
     */
    boolean existsByName(String name);
}
