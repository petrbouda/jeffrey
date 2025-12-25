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
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.RepositoryInfo;
import pbouda.jeffrey.common.model.workspace.RepositorySessionInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.platform.workspace.model.SessionCreatedEventContent;

import java.nio.file.Path;
import java.util.Optional;

public class CreateSessionWorkspaceEventConsumer implements WorkspaceEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(CreateSessionWorkspaceEventConsumer.class);

    private final ProjectsManager projectsManager;

    public CreateSessionWorkspaceEventConsumer(ProjectsManager projectsManager) {
        this.projectsManager = projectsManager;
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
        RepositorySessionInfo sessionInfo = new RepositorySessionInfo(
                null,
                repositoryInfo.get().id(),
                Path.of(eventContent.relativeSessionPath()),
                eventContent.finishedFile(),
                eventContent.profilerSettings(),
                event.originCreatedAt(),
                event.createdAt());

        projectManager.repositoryManager()
                .createSession(sessionInfo);
    }

    @Override
    public boolean isApplicable(WorkspaceEvent event) {
        return event.eventType() == WorkspaceEventType.SESSION_CREATED;
    }
}
