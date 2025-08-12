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

package pbouda.jeffrey.project.pipeline;

import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.IDGenerator;
import pbouda.jeffrey.common.model.GraphVisualization;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.pipeline.Stage;
import pbouda.jeffrey.configuration.properties.ProjectProperties;
import pbouda.jeffrey.provider.api.repository.ProjectsRepository;
import pbouda.jeffrey.provider.api.repository.model.CreateProject;

import java.time.Clock;
import java.util.Map;

public class CreateProjectStage implements Stage<CreateProjectContext> {

    private final ProjectsRepository projectsRepository;
    private final ProjectProperties projectProperties;
    private final Clock clock;

    public CreateProjectStage(ProjectsRepository projectsRepository, ProjectProperties projectProperties, Clock clock) {
        this.projectsRepository = projectsRepository;
        this.projectProperties = projectProperties;
        this.clock = clock;
    }

    @Override
    public CreateProjectContext execute(CreateProjectContext context) {
        Map<String, String> params = projectProperties.getParams();
        var graphVisualization = new GraphVisualization(
                Config.parseDouble(params, "graph-visualization.flamegraph-min-width", 0.00));

        ProjectInfo projectInfo = new ProjectInfo(
                IDGenerator.generate(),
                null,
                context.createProject().projectName(),
                context.createProject().workspaceId(),
                clock.instant(),
                context.createProject().attributes());

        CreateProject createProject = new CreateProject(projectInfo, graphVisualization);
        ProjectInfo newProjectInfo = projectsRepository.create(createProject);
        return context.withProjectInfo(newProjectInfo);
    }
}
