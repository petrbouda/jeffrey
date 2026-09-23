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

package cafe.jeffrey.microscope.core.web.dto.workspace;

import cafe.jeffrey.microscope.model.ProjectInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager.DetailedProjectInfo;
import cafe.jeffrey.shared.ui.hub.dto.ProjectResponse;
import cafe.jeffrey.shared.ui.hub.dto.WorkspaceResponse;


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
                detail.sessionCount(),
                detail.eventSource(),
                detail.isDeleted(),
                projectInfo.deletedAt() != null ? projectInfo.deletedAt().toEpochMilli() : null);
    }
}
