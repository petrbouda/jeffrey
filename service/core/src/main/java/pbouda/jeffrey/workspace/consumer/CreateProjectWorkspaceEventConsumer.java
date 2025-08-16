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

package pbouda.jeffrey.workspace.consumer;

import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.manager.model.CreateProject;
import pbouda.jeffrey.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.workspace.model.ProjectCreatedEvent;

public class CreateProjectWorkspaceEventConsumer implements WorkspaceEventConsumer {

    private final ProjectsManager projectsManager;

    public CreateProjectWorkspaceEventConsumer(ProjectsManager projectsManager) {
        this.projectsManager = projectsManager;
    }

    @Override
    public void on(WorkspaceEvent event, ProjectsSynchronizerJobDescriptor jobDescriptor) {
        if (event.eventType() == WorkspaceEventType.PROJECT_CREATED) {
            ProjectCreatedEvent eventContent = Json.read(event.content(), ProjectCreatedEvent.class);

            CreateProject createProject = new CreateProject(
                    event.projectId(),
                    eventContent.projectName(),
                    event.workspaceId(),
                    jobDescriptor.templateId(),
                    // When the project/event was created in the workspace (not replicated to the Jeffrey)
                    event.originCreatedAt(),
                    eventContent.attributes());

            projectsManager.create(createProject);
        }
    }
}
