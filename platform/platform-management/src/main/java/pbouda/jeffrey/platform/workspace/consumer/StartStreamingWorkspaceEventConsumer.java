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
import pbouda.jeffrey.platform.streaming.JfrStreamingConsumerManager;
import pbouda.jeffrey.platform.workspace.model.SessionCreatedEventContent;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Workspace event consumer that starts JFR streaming when a new session is created
 * with streaming enabled.
 */
public class StartStreamingWorkspaceEventConsumer implements WorkspaceEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(StartStreamingWorkspaceEventConsumer.class);

    private final ProjectsManager projectsManager;
    private final JfrStreamingConsumerManager streamingConsumerManager;

    public StartStreamingWorkspaceEventConsumer(
            ProjectsManager projectsManager,
            JfrStreamingConsumerManager streamingConsumerManager) {

        this.projectsManager = projectsManager;
        this.streamingConsumerManager = streamingConsumerManager;
    }

    @Override
    public void on(WorkspaceEvent event, ProjectsSynchronizerJobDescriptor jobDescriptor) {
        SessionCreatedEventContent eventContent = Json.read(event.content(), SessionCreatedEventContent.class);

        if (!eventContent.streamingEnabled()) {
            LOG.debug("Streaming not enabled for session, skipping: sessionId={}", event.originEventId());
            return;
        }

        Optional<ProjectManager> projectOpt = projectsManager.findByOriginProjectId(event.projectId());
        if (projectOpt.isEmpty()) {
            LOG.warn("Cannot start streaming for event, project not found: eventId={} sessionId={} projectId={}",
                    event.eventId(), event.originEventId(), event.projectId());
            return;
        }

        ProjectManager projectManager = projectOpt.get();
        Optional<RepositoryInfo> repositoryInfoOpt = projectManager.repositoryManager().info();
        if (repositoryInfoOpt.isEmpty()) {
            LOG.warn("Cannot start streaming for event, repository not found: eventId={} sessionId={} projectId={}",
                    event.eventId(), event.originEventId(), projectManager.info().id());
            return;
        }

        RepositoryInfo repositoryInfo = repositoryInfoOpt.get();
        ProjectInstanceSessionInfo sessionInfo = new ProjectInstanceSessionInfo(
                event.originEventId(),
                repositoryInfo.id(),
                eventContent.instanceId(),
                eventContent.order(),
                Path.of(eventContent.relativeSessionPath()),
                eventContent.profilerSettings(),
                eventContent.streamingEnabled(),
                event.originCreatedAt(),
                event.createdAt(),
                null);

        streamingConsumerManager.registerConsumer(repositoryInfo, sessionInfo);
    }

    @Override
    public boolean isApplicable(WorkspaceEvent event) {
        return event.eventType() == WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED;
    }
}
