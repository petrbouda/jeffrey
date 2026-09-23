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
package cafe.jeffrey.hub.core.manager.project;

import cafe.jeffrey.hub.model.CreateProject;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.persistence.api.ProjectsRepository;
import cafe.jeffrey.shared.common.IDGenerator;

import java.time.Clock;

/**
 * Turns a creation request into a project row of one workspace: a fresh id, the hub's clock
 * for {@code createdAt}, everything else as the request said. This was a one-stage pipeline
 * with a customizer nothing implemented; the stage is all there was.
 */
public class ProjectCreator {

    private final WorkspaceInfo workspaceInfo;
    private final ProjectsRepository projectsRepository;
    private final Clock clock;

    public ProjectCreator(WorkspaceInfo workspaceInfo, ProjectsRepository projectsRepository, Clock clock) {
        this.workspaceInfo = workspaceInfo;
        this.projectsRepository = projectsRepository;
        this.clock = clock;
    }

    public ProjectInfo create(CreateProject project) {
        ProjectInfo projectInfo = new ProjectInfo(
                IDGenerator.generate(),
                project.originProjectId(),
                project.projectName(),
                project.projectLabel(),
                project.namespace(),
                workspaceInfo.id(),
                clock.instant(),
                project.originCreatedAt(),
                project.attributes(),
                null);
        return projectsRepository.create(projectInfo);
    }
}
