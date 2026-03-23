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

package pbouda.jeffrey.server.core.resources.workspace;

import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.server.core.manager.project.ProjectManager.DetailedProjectInfo;
import pbouda.jeffrey.server.core.resources.response.ProjectResponse;
import pbouda.jeffrey.server.core.resources.response.WorkspaceResponse;
import pbouda.jeffrey.shared.common.InstantUtils;

public abstract class Mappers {

    public static WorkspaceResponse toResponse(WorkspaceInfo info) {
        return new WorkspaceResponse(
                info.id(),
                info.name(),
                info.description(),
                info.createdAt().toEpochMilli(),
                info.projectCount(),
                info.status());
    }

    public static ProjectResponse toProjectResponse(DetailedProjectInfo detail) {
        ProjectInfo projectInfo = detail.projectInfo();
        return new ProjectResponse(
                projectInfo.id(),
                projectInfo.originId(),
                projectInfo.name(),
                projectInfo.label(),
                projectInfo.namespace(),
                InstantUtils.formatInstant(projectInfo.createdAt()),
                projectInfo.workspaceId(),
                detail.status(),
                detail.sessionCount(),
                detail.isBlocked());
    }
}
