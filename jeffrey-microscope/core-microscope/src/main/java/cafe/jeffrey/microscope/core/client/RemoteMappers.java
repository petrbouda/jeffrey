/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.client;

import cafe.jeffrey.microscope.model.ProjectInfo;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager.DetailedProjectInfo;
import cafe.jeffrey.microscope.remote.client.dto.ProjectActivity;
import cafe.jeffrey.microscope.remote.client.dto.RemoteProjectResponse;

public abstract class RemoteMappers {

    public static DetailedProjectInfo toDetailedProjectInfo(ProjectInfo info, ProjectActivity activity) {
        return new DetailedProjectInfo(info, activity.lastDataAt(), activity.sessionCount(), null);
    }

    public static ProjectInfo toProjectInfo(RemoteProjectResponse response) {
        return new ProjectInfo(
                response.id(),
                response.id(),
                response.name(),
                null,
                response.workspaceId(),
                response.createdAt(),
                null,
                null);
    }
}
