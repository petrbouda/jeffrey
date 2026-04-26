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

package cafe.jeffrey.server.core.workspace.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.server.core.jfr.JfrMessageEmitter;
import cafe.jeffrey.server.core.manager.project.ProjectManager;
import cafe.jeffrey.server.core.manager.project.ProjectsManager;
import cafe.jeffrey.server.core.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import cafe.jeffrey.shared.common.model.workspace.event.InstanceCreatedEventContent;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.util.Optional;

public class InstanceCreatedWorkspaceEventConsumer implements WorkspaceEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(InstanceCreatedWorkspaceEventConsumer.class);

    @Override
    public void on(WorkspaceEvent event, ProjectsSynchronizerJobDescriptor jobDescriptor, ProjectsManager projectsManager) {
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
                event.originEventId(), // instanceName (same as instanceId for filesystem-based instances)
                ProjectInstanceStatus.PENDING,
                event.originCreatedAt(), // startedAt
                null, // finishedAt
                null, // expiringAt
                null, // expiredAt
                0, // sessionCount - will be calculated dynamically
                null); // activeSessionId - will be calculated dynamically

        projectManager.projectInstanceRepository().insert(instanceInfo);

        LOG.info("Instance created from workspace event: instance_id={} project_id={}",
                event.originEventId(), projectManager.info().id());
        JfrMessageEmitter.instanceCreated(event.originEventId(), projectManager.info().name(), projectManager.info().id());
    }

    @Override
    public boolean isApplicable(WorkspaceEvent event) {
        return event.eventType() == WorkspaceEventType.PROJECT_INSTANCE_CREATED;
    }
}
