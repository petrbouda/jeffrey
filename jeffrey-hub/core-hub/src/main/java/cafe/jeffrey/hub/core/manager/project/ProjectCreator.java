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
