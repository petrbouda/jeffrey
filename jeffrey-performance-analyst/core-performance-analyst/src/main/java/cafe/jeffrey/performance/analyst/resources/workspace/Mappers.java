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

package cafe.jeffrey.performance.analyst.resources.workspace;

import cafe.jeffrey.performance.analyst.resources.response.ProjectResponse;
import cafe.jeffrey.performance.analyst.resources.response.RemoteProjectResponse;
import cafe.jeffrey.performance.analyst.resources.response.WorkspaceResponse;
import cafe.jeffrey.shared.common.InstantUtils;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.time.Instant;

public abstract class Mappers {

    public static WorkspaceResponse toResponse(WorkspaceInfo info) {
        return new WorkspaceResponse(
                info.id(),
                info.name(),
                info.referenceId(),
                info.createdAt() != null ? info.createdAt().toEpochMilli() : 0L,
                info.projectCount(),
                info.status());
    }

    public static ProjectResponse toProjectResponse(RemoteProjectResponse project) {
        Instant createdAt = project.createdAt() != null ? InstantUtils.parseInstant(project.createdAt()) : null;
        return new ProjectResponse(
                project.id(),
                project.originId() != null ? project.originId() : project.id(),
                project.name(),
                project.label(),
                project.namespace(),
                createdAt != null ? createdAt.toEpochMilli() : 0L,
                project.workspaceId(),
                project.status(),
                project.sessionCount(),
                null,
                project.deletedAt() != null,
                project.deletedAt() != null ? project.deletedAt().toEpochMilli() : null);
    }
}
