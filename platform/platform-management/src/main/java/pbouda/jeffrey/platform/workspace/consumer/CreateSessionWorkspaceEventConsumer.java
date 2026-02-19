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
import pbouda.jeffrey.platform.streaming.HeartbeatReplayReader;
import pbouda.jeffrey.platform.streaming.SessionPaths;
import pbouda.jeffrey.platform.workspace.model.SessionCreatedEventContent;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.provider.platform.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class CreateSessionWorkspaceEventConsumer implements WorkspaceEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(CreateSessionWorkspaceEventConsumer.class);

    private final ProjectsManager projectsManager;
    private final PlatformRepositories platformRepositories;
    private final JeffreyDirs jeffreyDirs;
    private final Clock clock;

    public CreateSessionWorkspaceEventConsumer(
            ProjectsManager projectsManager,
            PlatformRepositories platformRepositories,
            JeffreyDirs jeffreyDirs,
            Clock clock) {

        this.projectsManager = projectsManager;
        this.platformRepositories = platformRepositories;
        this.jeffreyDirs = jeffreyDirs;
        this.clock = clock;
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
        closeUnfinishedSessions(repositoryRepository, repositoryInfo.get(), instanceId, event.originCreatedAt());
        LOG.info("Auto-closed unfinished sessions for instance before creating new session: project_id={} instance_id={}",
                projectId, instanceId);

        ProjectInstanceSessionInfo sessionInfo = new ProjectInstanceSessionInfo(
                event.originEventId(),
                repositoryInfo.get().id(),
                instanceId,
                eventContent.order(),
                Path.of(eventContent.relativeSessionPath()),
                eventContent.profilerSettings(),
                eventContent.streamingEnabled(),
                event.originCreatedAt(),
                event.createdAt(),
                null,
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

    /**
     * Closes unfinished sessions for an instance, using heartbeat replay for streaming sessions
     * to get accurate finished_at timestamps. Sessions are processed in chronological order so that
     * each session's fallback finished_at is the originCreatedAt of the next session in the sequence,
     * not the new event's timestamp.
     */
    private void closeUnfinishedSessions(
            ProjectRepositoryRepository repositoryRepository,
            RepositoryInfo repositoryInfo,
            String instanceId,
            Instant newSessionCreatedAt) {

        List<ProjectInstanceSessionInfo> unfinished = repositoryRepository.findUnfinishedSessions().stream()
                .filter(s -> s.instanceId().equals(instanceId))
                .sorted(Comparator.comparing(ProjectInstanceSessionInfo::originCreatedAt))
                .toList();

        for (int i = 0; i < unfinished.size(); i++) {
            ProjectInstanceSessionInfo session = unfinished.get(i);

            Path sessionPath = SessionPaths.resolve(jeffreyDirs, repositoryInfo, session);
            Instant replayFrom = session.lastHeartbeatAt() != null
                    ? session.lastHeartbeatAt()
                    : session.originCreatedAt();
            Optional<Instant> lastHeartbeat = HeartbeatReplayReader.readLastHeartbeat(sessionPath, replayFrom, clock);

            if (lastHeartbeat.isPresent()) {
                Instant hb = lastHeartbeat.get();
                repositoryRepository.markSessionFinishedWithHeartbeat(session.sessionId(), hb, hb);
                LOG.debug("Closed session with heartbeat timestamp: sessionId={} lastHeartbeat={}",
                        session.sessionId(), hb);
                continue;
            }

            // Use the next session's originCreatedAt as fallback, or the new session's createdAt for the last one
            Instant fallback = (i + 1 < unfinished.size())
                    ? unfinished.get(i + 1).originCreatedAt()
                    : newSessionCreatedAt;

            repositoryRepository.markSessionFinished(session.sessionId(), fallback);
            LOG.debug("Closed session with fallback timestamp: sessionId={} finishedAt={}",
                    session.sessionId(), fallback);
        }
    }

    @Override
    public boolean isApplicable(WorkspaceEvent event) {
        return event.eventType() == WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED;
    }
}
