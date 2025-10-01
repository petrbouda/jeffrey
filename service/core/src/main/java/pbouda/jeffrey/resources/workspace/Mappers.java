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

package pbouda.jeffrey.resources.workspace;

import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.manager.project.ProjectManager.DetailedProjectInfo;
import pbouda.jeffrey.resources.response.ProjectResponse;
import pbouda.jeffrey.resources.response.WorkspaceEventResponse;
import pbouda.jeffrey.resources.response.WorkspaceResponse;
import pbouda.jeffrey.resources.util.InstantUtils;

public abstract class Mappers {

    public static WorkspaceResponse toResponse(WorkspaceInfo info) {
        return new WorkspaceResponse(
                info.id(),
                info.name(),
                info.description(),
                InstantUtils.formatInstant(info.createdAt()),
                info.projectCount(),
                info.status(),
                info.type());
    }

    public static WorkspaceEventResponse toEventResponse(WorkspaceEvent event) {
        return new WorkspaceEventResponse(
                event.eventId(),
                event.originEventId(),
                event.projectId(),
                event.workspaceId(),
                event.eventType(),
                event.content(),
                event.originCreatedAt().toEpochMilli(),
                event.createdAt().toEpochMilli()
        );
    }

    public static ProjectResponse toProjectResponse(DetailedProjectInfo detail) {
        ProjectInfo projectInfo = detail.projectInfo();
        return new ProjectResponse(
                projectInfo.id(),
                projectInfo.name(),
                InstantUtils.formatInstant(projectInfo.createdAt()),
                projectInfo.workspaceId(),
                projectInfo.workspaceType(),
                detail.status(),
                detail.profileCount(),
                detail.recordingCount(),
                detail.sessionCount(),
                detail.jobCount(),
                detail.alertCount(),
                detail.eventSource());
    }
}
