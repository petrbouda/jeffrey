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

package cafe.jeffrey.server.core.project.pipeline;

import cafe.jeffrey.shared.common.Config;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.model.GraphVisualization;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.server.core.configuration.properties.ProjectProperties;
import cafe.jeffrey.server.persistence.repository.ProjectsRepository;
import cafe.jeffrey.server.persistence.model.CreateProject;

import java.time.Clock;
import java.util.Map;

public class CreateProjectStage implements Stage<CreateProjectContext> {

    private final WorkspaceInfo workspaceInfo;
    private final ProjectsRepository projectsRepository;
    private final ProjectProperties projectProperties;
    private final Clock clock;

    public CreateProjectStage(
            WorkspaceInfo workspaceInfo,
            ProjectsRepository projectsRepository,
            ProjectProperties projectProperties,
            Clock clock) {

        this.workspaceInfo = workspaceInfo;
        this.projectsRepository = projectsRepository;
        this.projectProperties = projectProperties;
        this.clock = clock;
    }

    @Override
    public CreateProjectContext execute(CreateProjectContext context) {
        Map<String, String> params = projectProperties.getParams();
        var graphVisualization = new GraphVisualization(
                Config.parseDouble(params, "graph-visualization.flamegraph-min-width", 0.00));

        cafe.jeffrey.shared.common.model.CreateProject project = context.createProject();

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

        CreateProject createProject = new CreateProject(projectInfo, graphVisualization);
        ProjectInfo newProjectInfo = projectsRepository.create(createProject);
        return context.withProjectInfo(newProjectInfo);
    }
}
