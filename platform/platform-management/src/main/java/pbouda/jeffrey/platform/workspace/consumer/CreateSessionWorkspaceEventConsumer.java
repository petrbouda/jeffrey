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
import pbouda.jeffrey.platform.workspace.model.SessionCreatedEventContent;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.provider.platform.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.nio.file.Path;
import java.util.Optional;

public class CreateSessionWorkspaceEventConsumer implements WorkspaceEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(CreateSessionWorkspaceEventConsumer.class);

    private final ProjectsManager projectsManager;
    private final PlatformRepositories platformRepositories;

    public CreateSessionWorkspaceEventConsumer(ProjectsManager projectsManager, PlatformRepositories platformRepositories) {
        this.projectsManager = projectsManager;
        this.platformRepositories = platformRepositories;
    }

    @Override
    public void on(WorkspaceEvent event, ProjectsSynchronizerJobDescriptor jobDescriptor) {
        SessionCreatedEventContent eventContent = Json.read(event.content(), SessionCreatedEventContent.class);

        Optional<ProjectManager> projectOpt = projectsManager.findByOriginProjectId(event.projectId());
        if (projectOpt.isEmpty()) {
            LOG.warn("Cannot create session for event, project not found: event_id={}, session_id={} project_id={}",
                    event.eventId(), event.originEventId(), event.projectId());
            return;
        }

        ProjectManager projectManager = projectOpt.get();
        Optional<RepositoryInfo> repositoryInfo = projectManager.repositoryManager().info();
        if (repositoryInfo.isEmpty()) {
            LOG.warn("Cannot create session for event, project repository not found: " +
                            "event_id={}, session_id={} project_id={}",
                    event.eventId(), event.originEventId(), projectManager.info().id());
            return;
        }

        String projectId = projectManager.info().id();
        String instanceId = eventContent.instanceId();

        ProjectRepositoryRepository repositoryRepository = platformRepositories.newProjectRepositoryRepository(projectId);
        repositoryRepository.markUnfinishedSessionsFinished(instanceId, event.createdAt());
        LOG.info("Auto-closed unfinished sessions for instance before creating new session: project_id={} instance_id={}",
                projectId, instanceId);

        ProjectInstanceSessionInfo sessionInfo = new ProjectInstanceSessionInfo(
                null,
                repositoryInfo.get().id(),
                instanceId,
                eventContent.order(),
                Path.of(eventContent.relativeSessionPath()),
                eventContent.finishedFile(),
                eventContent.profilerSettings(),
                eventContent.streamingEnabled(),
                event.originCreatedAt(),
                event.createdAt(),
                null);

        projectManager.repositoryManager()
                .createSession(sessionInfo);

        if (eventContent.order() > 1) {
            platformRepositories.newProjectInstanceRepository(projectId)
                    .reactivate(instanceId);
            LOG.debug("Instance re-activated after new session created: project_id={} instance_id={}", projectId, instanceId);
        }

        LOG.debug("Session created from workspace event: project_id={} instance_id={} session_id={}", projectId, instanceId, event.originEventId());
    }

    @Override
    public boolean isApplicable(WorkspaceEvent event) {
        return event.eventType() == WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED;
    }
}
