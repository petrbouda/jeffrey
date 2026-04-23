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

package pbouda.jeffrey.server.core.workspace.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.server.core.jfr.JfrMessageEmitter;
import pbouda.jeffrey.server.core.manager.project.ProjectManager;
import pbouda.jeffrey.server.core.manager.project.ProjectsManager;
import pbouda.jeffrey.server.core.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.server.core.streaming.SessionFinisher;
import pbouda.jeffrey.server.core.streaming.SessionPaths;
import pbouda.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import pbouda.jeffrey.server.persistence.repository.ProjectInstanceRepository;
import pbouda.jeffrey.server.persistence.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.server.core.ServerJeffreyDirs;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.shared.common.model.workspace.event.SessionCreatedEventContent;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class CreateSessionWorkspaceEventConsumer implements WorkspaceEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(CreateSessionWorkspaceEventConsumer.class);

    private final ServerPlatformRepositories platformRepositories;
    private final ServerJeffreyDirs jeffreyDirs;
    private final SessionFinisher sessionFinisher;

    public CreateSessionWorkspaceEventConsumer(
            ServerPlatformRepositories platformRepositories,
            ServerJeffreyDirs jeffreyDirs,
            SessionFinisher sessionFinisher) {

        this.platformRepositories = platformRepositories;
        this.jeffreyDirs = jeffreyDirs;
        this.sessionFinisher = sessionFinisher;
    }

    @Override
    public void on(WorkspaceEvent event, ProjectsSynchronizerJobDescriptor jobDescriptor, ProjectsManager projectsManager) {
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

        ProjectInfo projectInfo = projectManager.info();
        String projectId = projectInfo.id();
        String instanceId = eventContent.instanceId();

        ProjectRepositoryRepository repositoryRepository = platformRepositories.newProjectRepositoryRepository(projectId);
        int closedCount = closeUnfinishedSessions(repositoryRepository, projectInfo, repositoryInfo.get(), instanceId, event.originCreatedAt());
        if (closedCount > 0) {
            LOG.info("Auto-closed unfinished sessions for instance before creating new session: project_id={} instance_id={} closed={}",
                    projectId, instanceId, closedCount);
        }

        ProjectInstanceSessionInfo sessionInfo = new ProjectInstanceSessionInfo(
                event.originEventId(),
                repositoryInfo.get().id(),
                instanceId,
                eventContent.order(),
                Path.of(eventContent.relativeSessionPath()),
                event.originCreatedAt(),
                event.createdAt(),
                null);

        projectManager.repositoryManager()
                .createSession(sessionInfo);

        // Transition instance to ACTIVE (handles PENDING→ACTIVE, FINISHED→ACTIVE, EXPIRED→ACTIVE)
        ProjectInstanceRepository instanceRepo = platformRepositories.newProjectInstanceRepository(projectId);
        instanceRepo.updateStatus(instanceId, ProjectInstanceStatus.ACTIVE);

        LOG.debug("Session created from workspace event: project_id={} instance_id={} session_id={}", projectId, instanceId, event.originEventId());
        JfrMessageEmitter.sessionCreated(event.originEventId(), instanceId, eventContent.order(), projectId);
    }

    /**
     * Closes unfinished sessions for an instance by delegating to {@link SessionFinisher#forceFinish}.
     * Sessions are processed in chronological order so that each session's fallback finished_at
     * is the originCreatedAt of the next session in the sequence, not the new event's timestamp.
     */
    private int closeUnfinishedSessions(
            ProjectRepositoryRepository repositoryRepository,
            ProjectInfo projectInfo,
            RepositoryInfo repositoryInfo,
            String instanceId,
            Instant newSessionCreatedAt) {

        List<ProjectInstanceSessionInfo> unfinished =
                repositoryRepository.findUnfinishedSessionsByInstanceId(instanceId).stream()
                        .sorted(Comparator.comparing(ProjectInstanceSessionInfo::originCreatedAt))
                        .toList();

        for (int i = 0; i < unfinished.size(); i++) {
            ProjectInstanceSessionInfo session = unfinished.get(i);
            Path sessionPath = SessionPaths.resolve(jeffreyDirs, repositoryInfo, session);

            // Use the next session's originCreatedAt as fallback, or the new session's createdAt for the last one
            Instant fallback = (i + 1 < unfinished.size())
                    ? unfinished.get(i + 1).originCreatedAt()
                    : newSessionCreatedAt;

            sessionFinisher.forceFinish(repositoryRepository, projectInfo, session, sessionPath, fallback);
        }

        return unfinished.size();
    }

    @Override
    public boolean isApplicable(WorkspaceEvent event) {
        return event.eventType() == WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED;
    }
}
