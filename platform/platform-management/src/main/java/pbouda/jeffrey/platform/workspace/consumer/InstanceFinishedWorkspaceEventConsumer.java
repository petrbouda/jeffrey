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
import pbouda.jeffrey.platform.jfr.JfrEmitter;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.provider.platform.repository.ProjectInstanceRepository;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.util.Optional;

public class InstanceFinishedWorkspaceEventConsumer implements WorkspaceEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(InstanceFinishedWorkspaceEventConsumer.class);

    private final ProjectsManager projectsManager;
    private final PlatformRepositories platformRepositories;

    public InstanceFinishedWorkspaceEventConsumer(ProjectsManager projectsManager, PlatformRepositories platformRepositories) {
        this.projectsManager = projectsManager;
        this.platformRepositories = platformRepositories;
    }

    @Override
    public void on(WorkspaceEvent event, ProjectsSynchronizerJobDescriptor jobDescriptor) {
        Optional<ProjectManager> projectOpt = projectsManager.findByOriginProjectId(event.projectId());
        if (projectOpt.isEmpty()) {
            LOG.warn("Cannot mark instance finished for event, project not found: event_id={} instance_id={} project_id={}",
                    event.eventId(), event.originEventId(), event.projectId());
            return;
        }

        ProjectManager projectManager = projectOpt.get();
        String projectId = projectManager.info().id();
        String instanceId = event.originEventId();

        ProjectInstanceRepository projectInstanceRepository =
                platformRepositories.newProjectInstanceRepository(projectId);

        projectInstanceRepository.updateStatus(instanceId, ProjectInstanceStatus.FINISHED);
        projectInstanceRepository.markFinished(instanceId, event.originCreatedAt());

        JfrEmitter.instanceFinished(instanceId, projectId);
        LOG.info("Instance marked as finished from workspace event: instance_id={} project_id={}",
                instanceId, projectId);
    }

    @Override
    public boolean isApplicable(WorkspaceEvent event) {
        return event.eventType() == WorkspaceEventType.PROJECT_INSTANCE_FINISHED;
    }
}
