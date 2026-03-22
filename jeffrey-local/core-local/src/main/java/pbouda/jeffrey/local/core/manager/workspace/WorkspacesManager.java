/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package pbouda.jeffrey.local.core.manager.workspace;

import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceLocation;

import java.util.List;
import java.util.Optional;

public interface WorkspacesManager {

    record CreateWorkspaceRequest(
            String workspaceId,
            String workspaceSourceId,
            String name,
            String description,
            WorkspaceLocation location,
            WorkspaceLocation baseLocation) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String workspaceId;
            private String workspaceSourceId;
            private String name;
            private String description;
            private WorkspaceLocation workspaceLocation;
            private WorkspaceLocation workspaceBaseLocation;

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
                this.workspaceLocation = location != null ? WorkspaceLocation.of(location) : null;
                return this;
            }

            public Builder location(WorkspaceLocation location) {
                this.workspaceLocation = location;
                return this;
            }

            public Builder baseLocation(WorkspaceLocation baseLocation) {
                this.workspaceBaseLocation = baseLocation;
                return this;
            }

            public CreateWorkspaceRequest build() {
                return new CreateWorkspaceRequest(
                        workspaceId,
                        workspaceSourceId,
                        name,
                        description,
                        workspaceLocation,
                        workspaceBaseLocation);
            }
        }
    }

    WorkspaceInfo create(CreateWorkspaceRequest request);

    List<? extends WorkspaceManager> findAll();

    Optional<WorkspaceManager> findById(String workspaceId);
}
