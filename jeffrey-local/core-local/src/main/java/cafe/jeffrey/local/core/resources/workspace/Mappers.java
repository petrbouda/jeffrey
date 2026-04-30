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

package cafe.jeffrey.local.core.resources.workspace;

import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.local.core.manager.project.ProjectManager.DetailedProjectInfo;
import cafe.jeffrey.local.core.resources.response.ProjectResponse;
import cafe.jeffrey.local.core.resources.response.WorkspaceEventResponse;
import cafe.jeffrey.local.core.resources.response.WorkspaceResponse;


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

    public static WorkspaceEventResponse toEventResponse(WorkspaceEvent event) {
        return new WorkspaceEventResponse(
                event.eventId(),
                event.originEventId(),
                event.projectId(),
                event.workspaceRefId(),
                event.eventType(),
                event.content(),
                event.originCreatedAt().toEpochMilli(),
                event.createdAt().toEpochMilli(),
                event.createdBy()
        );
    }

    public static ProjectResponse toProjectResponse(DetailedProjectInfo detail) {
        ProjectInfo projectInfo = detail.projectInfo();
        return new ProjectResponse(
                projectInfo.id(),
                projectInfo.id(),
                projectInfo.name(),
                projectInfo.label(),
                projectInfo.namespace(),
                projectInfo.createdAt().toEpochMilli(),
                projectInfo.workspaceId(),
                detail.status(),
                detail.profileCount(),
                detail.recordingCount(),
                detail.sessionCount(),
                detail.eventSource(),
                detail.isDeleted(),
                projectInfo.deletedAt() != null ? projectInfo.deletedAt().toEpochMilli() : null);
    }
}
