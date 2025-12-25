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

package pbouda.jeffrey.platform.manager.workspace;

import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceLocation;
import pbouda.jeffrey.common.model.workspace.WorkspaceType;

import java.util.List;
import java.util.Optional;

public sealed interface WorkspacesManager
        permits CompositeWorkspacesManager, LiveWorkspacesManager, RemoteWorkspacesManager, SandboxWorkspacesManager {

    record CreateWorkspaceRequest(
            String workspaceId,
            String workspaceSourceId,
            String name,
            String description,
            WorkspaceLocation location,
            WorkspaceLocation baseLocation,
            WorkspaceType type) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String workspaceId;
            private String workspaceSourceId;
            private String name;
            private String description;
            private String location;
            private WorkspaceLocation workspaceLocation;
            private String baseLocation;
            private WorkspaceLocation workspaceBaseLocation;
            private WorkspaceType type;

            public Builder workspaceId(String workspaceId) {
                this.workspaceId = workspaceId;
                return this;
            }

            public Builder workspaceSourceId(String workspaceSourceId) {
                this.workspaceSourceId = workspaceSourceId;
                return this;
            }

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public Builder location(String location) {
                this.location = location;
                return this;
            }

            public Builder location(WorkspaceLocation location) {
                this.workspaceLocation = location;
                return this;
            }

            public Builder baseLocation(String baseLocation) {
                this.baseLocation = baseLocation;
                return this;
            }

            public Builder baseLocation(WorkspaceLocation baseLocation) {
                this.workspaceBaseLocation = baseLocation;
                return this;
            }

            public Builder type(WorkspaceType type) {
                this.type = type;
                return this;
            }

            public CreateWorkspaceRequest build() {
                WorkspaceLocation location = this.workspaceLocation;
                if (location == null && this.location != null) {
                    location = WorkspaceLocation.of(this.location);
                }

                WorkspaceLocation baseLocation = this.workspaceBaseLocation;
                if (baseLocation == null && this.baseLocation != null) {
                    baseLocation = WorkspaceLocation.of(this.baseLocation);
                }

                return new CreateWorkspaceRequest(
                        workspaceId,
                        workspaceSourceId,
                        name,
                        description,
                        location,
                        baseLocation,
                        type);
            }
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
    List<? extends WorkspaceManager> findAll();

    /**
     * Get a workspace by its ID.
     *
     * @param workspaceId the workspace ID
     * @return the workspace if it exists, otherwise an empty optional
     */
    Optional<WorkspaceManager> findById(String workspaceId);

    /**
     * Map WorkspaceInfo to WorkspaceManager.
     *
     * @param info the workspace info.
     * @return mapped workspace manager.
     */
    WorkspaceManager mapToWorkspaceManager(WorkspaceInfo info);
}
