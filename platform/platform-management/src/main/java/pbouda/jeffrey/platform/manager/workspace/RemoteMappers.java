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

import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.platform.manager.project.ProjectManager.DetailedProjectInfo;
import pbouda.jeffrey.platform.resources.response.ProjectResponse;
import pbouda.jeffrey.platform.resources.util.InstantUtils;

import java.util.Optional;

public abstract class RemoteMappers {

    public static DetailedProjectInfo toDetailedProjectInfo(
            ProjectResponse response, Optional<ProjectInfo> liveProject) {

        return new DetailedProjectInfo(
                toProjectInfo(response, liveProject),
                response.status(),
                response.profileCount(),
                response.recordingCount(),
                response.sessionCount(),
                response.jobCount(),
                response.alertCount(),
                response.eventSource(),
                liveProject.isEmpty(),
                false);
    }

    private static ProjectInfo toProjectInfo(ProjectResponse response, Optional<ProjectInfo> liveProject) {
        return new ProjectInfo(
                liveProject.map(ProjectInfo::id).orElse(response.id()),
                response.id(),
                response.name(),
                response.label(),
                liveProject.map(ProjectInfo::namespace).orElse(null),
                response.workspaceId(),
                WorkspaceType.REMOTE,
                InstantUtils.parseInstant(response.createdAt()),
                null,
                null
        );
    }
}
