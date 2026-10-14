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
     * What a workspace is created from: its id, which is the reference id the provisioner names
     * its directory by, and a display name.
     */
    record CreateWorkspaceRequest(String referenceId, String name) {

        public CreateWorkspaceRequest {
            WorkspaceReferenceId.validate(referenceId);
        }
    }

    /**
     * Creates the workspace's directory and marker, or adopts a directory the provisioner already
     * wrote into by giving it a marker.
     *
     * @throws WorkspaceAlreadyExistsException when a workspace of that id or name exists
     */
    WorkspaceInfo create(CreateWorkspaceRequest request);

    /** Every workspace directory on the volume. */
    List<WorkspaceManager> findAll();

    /**
     * @param workspaceId the workspace's directory name, which is its reference id
     */
    Optional<WorkspaceManager> findById(String workspaceId);

    /**
     * Whether the workspace was created — its directory carries the hub's marker — as opposed to
     * listed only because auto-create stands it in for a directory the provisioner wrote into.
     */
    boolean isDescribed(String workspaceId);
}
