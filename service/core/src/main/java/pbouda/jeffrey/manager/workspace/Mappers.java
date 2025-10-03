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

package pbouda.jeffrey.manager.workspace;

import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.manager.project.ProjectManager.DetailedProjectInfo;
import pbouda.jeffrey.resources.response.ProjectResponse;
import pbouda.jeffrey.resources.util.InstantUtils;

public abstract class Mappers {

    public static DetailedProjectInfo toDetailedProjectInfo(ProjectResponse response) {
        return new DetailedProjectInfo(
                toProjectInfo(response),
                response.status(),
                response.profileCount(),
                response.recordingCount(),
                response.sessionCount(),
                response.jobCount(),
                response.alertCount(),
                response.eventSource());
    }

    private static ProjectInfo toProjectInfo(ProjectResponse response) {
        return new ProjectInfo(
                response.id(),
                null,
                response.name(),
                response.workspaceId(),
                response.workspaceType(),
                InstantUtils.parseInstant(response.createdAt()),
                null,
                null
        );
    }
}
