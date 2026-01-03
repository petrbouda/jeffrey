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

package pbouda.jeffrey.platform.workspace.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.profile.manager.model.CreateProject;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.platform.workspace.model.ProjectCreatedEventContent;

public class CreateProjectWorkspaceEventConsumer implements WorkspaceEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(CreateProjectWorkspaceEventConsumer.class);

    private final ProjectsManager projectsManager;

    public CreateProjectWorkspaceEventConsumer(ProjectsManager projectsManager) {
        this.projectsManager = projectsManager;
    }

    @Override
    public void on(WorkspaceEvent event, ProjectsSynchronizerJobDescriptor jobDescriptor) {
        ProjectCreatedEventContent eventContent = Json.read(event.content(), ProjectCreatedEventContent.class);
        CreateProject createProject = new CreateProject(
                event.projectId(),
                eventContent.projectName(),
                eventContent.projectLabel(),
                jobDescriptor.templateId(),
                // When the project/event was created in the workspace (not replicated to the Jeffrey)
                event.originCreatedAt(),
                eventContent.attributes());

        ProjectManager projectManager = projectsManager.create(createProject);

        LOG.info("Project created from workspace event: project_id={} event={}",
                projectManager.info().id(), event);

        RepositoryInfo projectRepository = new RepositoryInfo(
                null,
                eventContent.repositoryType(),
                eventContent.workspacesPath(),
                eventContent.relativeWorkspacePath(),
                eventContent.relativeProjectPath());

        projectManager.repositoryManager()
                .create(projectRepository);
    }

    @Override
    public boolean isApplicable(WorkspaceEvent event) {
        return event.eventType() == WorkspaceEventType.PROJECT_CREATED;
    }
}
