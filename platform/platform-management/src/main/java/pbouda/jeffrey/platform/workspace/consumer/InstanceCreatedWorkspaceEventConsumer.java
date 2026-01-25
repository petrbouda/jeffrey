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
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.platform.workspace.model.InstanceCreatedEventContent;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.util.Optional;

public class InstanceCreatedWorkspaceEventConsumer implements WorkspaceEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(InstanceCreatedWorkspaceEventConsumer.class);

    private final ProjectsManager projectsManager;

    public InstanceCreatedWorkspaceEventConsumer(ProjectsManager projectsManager) {
        this.projectsManager = projectsManager;
    }

    @Override
    public void on(WorkspaceEvent event, ProjectsSynchronizerJobDescriptor jobDescriptor) {
        InstanceCreatedEventContent eventContent = Json.read(event.content(), InstanceCreatedEventContent.class);

        Optional<ProjectManager> projectOpt = projectsManager.findByOriginProjectId(event.projectId());
        if (projectOpt.isEmpty()) {
            LOG.warn("Cannot create instance for event, project not found: event_id={} instance_id={} project_id={}",
                    event.eventId(), event.originEventId(), event.projectId());
            return;
        }

        ProjectManager projectManager = projectOpt.get();

        // Check if instance already exists
        Optional<ProjectInstanceInfo> existingInstance = projectManager.projectInstanceRepository()
                .find(event.originEventId());

        if (existingInstance.isPresent()) {
            LOG.debug("Instance already exists, skipping: instance_id={} project_id={}",
                    event.originEventId(), projectManager.info().id());
            return;
        }

        // Create new instance
        ProjectInstanceInfo instanceInfo = new ProjectInstanceInfo(
                event.originEventId(), // instanceId
                projectManager.info().id(),
                event.originEventId(), // hostname (same as instanceId for filesystem-based instances)
                ProjectInstanceStatus.OFFLINE, // Default to OFFLINE for filesystem-based instances
                event.originCreatedAt(), // lastHeartbeat
                event.originCreatedAt(), // startedAt
                0, // sessionCount - will be calculated dynamically
                null); // activeSessionId - will be calculated dynamically

        projectManager.projectInstanceRepository().insert(instanceInfo);

        LOG.info("Instance created from workspace event: instance_id={} project_id={}",
                event.originEventId(), projectManager.info().id());
    }

    @Override
    public boolean isApplicable(WorkspaceEvent event) {
        return event.eventType() == WorkspaceEventType.PROJECT_INSTANCE_CREATED;
    }
}
