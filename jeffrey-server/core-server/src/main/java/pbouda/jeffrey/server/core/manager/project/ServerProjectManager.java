/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package pbouda.jeffrey.server.core.manager.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import pbouda.jeffrey.server.core.manager.*;
import pbouda.jeffrey.server.core.project.repository.RepositoryStorage;
import pbouda.jeffrey.server.core.scheduler.SchedulerTrigger;
import pbouda.jeffrey.server.core.streaming.JfrStreamingConsumerManager;
import pbouda.jeffrey.server.core.workspace.WorkspaceEventConverter;
import pbouda.jeffrey.server.core.workspace.WorkspaceEventPublisher;
import pbouda.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import pbouda.jeffrey.server.persistence.repository.ProjectInstanceRepository;
import pbouda.jeffrey.server.persistence.repository.ProjectRepository;
import pbouda.jeffrey.server.persistence.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.server.persistence.repository.SchedulerRepository;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import pbouda.jeffrey.shared.common.model.job.JobInfo;
import pbouda.jeffrey.shared.common.model.repository.RecordingSession;
import pbouda.jeffrey.shared.common.model.repository.RecordingStatus;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.time.Clock;
import java.util.List;

/**
 * Server-specific project manager — no profile analysis, recording management,
 * or download capabilities. The server is a pure collector.
 */
public class ServerProjectManager implements ProjectManager {

    private static final Logger LOG = LoggerFactory.getLogger(ServerProjectManager.class);

    private final ProjectInfo projectInfo;
    private final ProjectRepository projectRepository;
    private final SchedulerRepository schedulerRepository;
    private final ServerPlatformRepositories platformRepositories;
    private final RepositoryStorage repositoryStorage;
    private final WorkspaceEventPublisher workspaceEventPublisher;
    private final JfrStreamingConsumerManager jfrStreamingConsumerManager;
    private final Clock clock;
    private final ObjectFactory<SchedulerTrigger> projectsSynchronizerTrigger;

    public ServerProjectManager(
            Clock clock,
            ProjectInfo projectInfo,
            ObjectFactory<SchedulerTrigger> projectsSynchronizerTrigger,
            ServerPlatformRepositories platformRepositories,
            RepositoryStorage repositoryStorage,
            WorkspaceEventPublisher workspaceEventPublisher,
            JfrStreamingConsumerManager jfrStreamingConsumerManager) {

        this.clock = clock;
        String projectId = projectInfo.id();
        this.projectsSynchronizerTrigger = projectsSynchronizerTrigger;
        this.projectInfo = projectInfo;
        this.projectRepository = platformRepositories.newProjectRepository(projectId);
        this.schedulerRepository = platformRepositories.newProjectSchedulerRepository(projectId);
        this.platformRepositories = platformRepositories;
        this.repositoryStorage = repositoryStorage;
        this.workspaceEventPublisher = workspaceEventPublisher;
        this.jfrStreamingConsumerManager = jfrStreamingConsumerManager;
    }

    @Override
    public MessagesManager messagesManager() {
        return new MessagesManagerImpl(
                clock,
                platformRepositories.newMessageRepository(projectInfo.id()),
                platformRepositories.newAlertRepository(projectInfo.id()));
    }

    @Override
    public RepositoryStorage repositoryStorage() {
        return repositoryStorage;
    }

    @Override
    public RepositoryManager repositoryManager() {
        return new RepositoryManagerImpl(
                clock,
                projectInfo,
                projectsSynchronizerTrigger.getObject(),
                platformRepositories.newProjectRepositoryRepository(projectInfo.id()),
                repositoryStorage,
                workspaceEventPublisher);
    }

    @Override
    public SchedulerManager schedulerManager() {
        return new SchedulerManagerImpl(schedulerRepository);
    }

    @Override
    public ProfilerSettingsManager profilerSettingsManager() {
        return new LiveProfilerSettingsManager(
                platformRepositories.newProfilerRepository(),
                projectInfo.workspaceId(),
                projectInfo.id());
    }

    @Override
    public ProjectInstanceRepository projectInstanceRepository() {
        return platformRepositories.newProjectInstanceRepository(projectInfo.id());
    }

    @Override
    public ProjectInfo info() {
        return projectInfo;
    }

    @Override
    public DetailedProjectInfo detailedInfo() {
        List<RecordingSession> recordingSessions = repositoryManager()
                .listRecordingSessions(false);

        RecordingStatus recordingStatus = recordingSessions.stream()
                .limit(1)
                .findAny()
                .map(RecordingSession::status).orElse(null);

        return new DetailedProjectInfo(
                projectInfo,
                recordingStatus,
                recordingSessions.size(),
                projectInfo.blocked());
    }

    @Override
    public void block() {
        projectRepository.block();

        // Stop all active JFR streaming consumers for this project
        ProjectRepositoryRepository repoRepository =
                platformRepositories.newProjectRepositoryRepository(projectInfo.id());
        List<ProjectInstanceSessionInfo> unfinishedSessions = repoRepository.findUnfinishedSessions();
        for (ProjectInstanceSessionInfo session : unfinishedSessions) {
            jfrStreamingConsumerManager.unregisterConsumer(session.sessionId());
        }

        LOG.info("Blocked project: projectId={} stoppedStreams={}", projectInfo.id(), unfinishedSessions.size());
    }

    @Override
    public void unblock() {
        projectRepository.unblock();
        LOG.info("Unblocked project: projectId={}", projectInfo.id());
    }

    @Override
    public void updateStreamingEnabled(Boolean enabled) {
        projectRepository.updateStreamingEnabled(enabled);

        // When streaming is explicitly disabled, stop active consumers
        if (Boolean.FALSE.equals(enabled)) {
            ProjectRepositoryRepository repoRepository =
                    platformRepositories.newProjectRepositoryRepository(projectInfo.id());
            List<ProjectInstanceSessionInfo> unfinishedSessions = repoRepository.findUnfinishedSessions();
            for (ProjectInstanceSessionInfo session : unfinishedSessions) {
                jfrStreamingConsumerManager.unregisterConsumer(session.sessionId());
            }
            LOG.info("Updated project streaming to disabled: projectId={} stoppedStreams={}",
                    projectInfo.id(), unfinishedSessions.size());
        } else {
            LOG.info("Updated project streaming: projectId={} enabled={}", projectInfo.id(), enabled);
        }
    }

    @Override
    public void delete(WorkspaceEventCreator createdBy) {
        LOG.debug("Deleting project: projectId={}", info().id());

        WorkspaceEvent workspaceEvent = WorkspaceEventConverter.projectDeleted(
                clock.instant(),
                projectInfo.workspaceId(),
                projectInfo.id(),
                createdBy);

        workspaceEventPublisher.publishBatch(projectInfo.workspaceId(), List.of(workspaceEvent));
        projectsSynchronizerTrigger.getObject().execute();
    }
}
