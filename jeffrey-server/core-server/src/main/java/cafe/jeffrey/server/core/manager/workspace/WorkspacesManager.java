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

package cafe.jeffrey.server.core.manager.workspace;

import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceLocation;

import java.util.List;
import java.util.Optional;

public interface WorkspacesManager {

    record CreateWorkspaceRequest(
            String workspaceId,
            String referenceId,
            String name,
            WorkspaceLocation location,
            WorkspaceLocation baseLocation) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String workspaceId;
            private String referenceId;
            private String name;
            private String location;
            private WorkspaceLocation workspaceLocation;
            private String baseLocation;
            private WorkspaceLocation workspaceBaseLocation;

            public Builder workspaceId(String workspaceId) {
                this.workspaceId = workspaceId;
                return this;
            }

            public Builder referenceId(String referenceId) {
                this.referenceId = referenceId;
                return this;
            }

            public Builder name(String name) {
                this.name = name;
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
                        referenceId,
                        name,
                        location,
                        baseLocation);
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
     * Get a workspace by its reference ID (the user-supplied id the CLI binds to).
     *
     * @param referenceId the workspace reference ID
     * @return the workspace if it exists, otherwise an empty optional
     */
    Optional<WorkspaceManager> findByReferenceId(String referenceId);

}
