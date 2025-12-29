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

package pbouda.jeffrey.shared.model.workspace;

import java.time.Instant;

public record WorkspaceInfo(
        String id,
        String originId,
        String repositoryId,
        String name,
        String description,
        WorkspaceLocation location,
        WorkspaceLocation baseLocation,
        Instant createdAt,
        WorkspaceType type,
        WorkspaceStatus status,
        int projectCount) {

    public WorkspaceInfo withId(String newId) {
        return new WorkspaceInfo(
                newId,
                this.originId,
                this.repositoryId,
                this.name,
                this.description,
                this.location,
                this.baseLocation,
                this.createdAt,
                this.type,
                this.status,
                this.projectCount
        );
    }

    public WorkspaceInfo withLocation(WorkspaceLocation newLocation) {
        return new WorkspaceInfo(
                this.id,
                this.originId,
                this.repositoryId,
                this.name,
                this.description,
                newLocation,
                this.baseLocation,
                this.createdAt,
                this.type,
                this.status,
                this.projectCount
        );
    }

    public WorkspaceInfo withStatus(WorkspaceStatus newStatus) {
        return new WorkspaceInfo(
                this.id,
                this.originId,
                this.repositoryId,
                this.name,
                this.description,
                this.location,
                this.baseLocation,
                this.createdAt,
                this.type,
                newStatus,
                this.projectCount
        );
    }

    public boolean isLive() {
        return this.type == WorkspaceType.LIVE;
    }

    public boolean isSandbox() {
        return this.type == WorkspaceType.SANDBOX;
    }

    public boolean isRemote() {
        return this.type == WorkspaceType.REMOTE;
    }
}
