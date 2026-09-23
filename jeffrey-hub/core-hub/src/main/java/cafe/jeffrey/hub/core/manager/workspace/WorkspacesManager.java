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

package cafe.jeffrey.hub.core.manager.workspace;

import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.model.workspace.WorkspaceReferenceId;

import java.util.List;
import java.util.Optional;

public interface WorkspacesManager {

    /**
     * What a workspace is created from: the reference id the provisioner names it by on the
     * volume, and a display name. It once carried a location and a base location too, which no
     * caller ever set — a workspace's directory is derived from its repository id.
     */
    record CreateWorkspaceRequest(String referenceId, String name) {

        public CreateWorkspaceRequest {
            WorkspaceReferenceId.validate(referenceId);
        }
    }

    /**
     * Create a new workspace.
     *
     * @param request the workspace creation request
     * @return the created workspace
     */
    WorkspaceInfo create(CreateWorkspaceRequest request);

    /**
     * Get all workspaces.
     *
     * @return list of all workspaces
     */
    List<WorkspaceManager> findAll();

    /**
     * Get a workspace by its ID.
     *
     * @param workspaceId the workspace ID
     * @return the workspace if it exists, otherwise an empty optional
     */
    Optional<WorkspaceManager> findById(String workspaceId);

    /**
     * Get a workspace by its reference ID (the user-supplied id the CLI binds to).
     *
     * @param referenceId the workspace reference ID
     * @return the workspace if it exists, otherwise an empty optional
     */
    Optional<WorkspaceManager> findByReferenceId(String referenceId);

}
