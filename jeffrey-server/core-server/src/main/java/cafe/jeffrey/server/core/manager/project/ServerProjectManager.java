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

package cafe.jeffrey.server.core.manager.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import cafe.jeffrey.server.core.manager.LiveProfilerSettingsManager;
import cafe.jeffrey.server.core.manager.ProfilerSettingsManager;
import cafe.jeffrey.server.core.manager.RepositoryManager;
import cafe.jeffrey.server.core.manager.RepositoryManagerImpl;
import cafe.jeffrey.server.core.project.repository.InstanceEnvironmentParser;
import cafe.jeffrey.server.core.project.repository.RepositoryStorage;
import cafe.jeffrey.server.core.scheduler.SchedulerTrigger;
import cafe.jeffrey.server.core.workspace.WorkspaceEventConverter;
import cafe.jeffrey.server.core.workspace.WorkspaceEventPublisher;
import cafe.jeffrey.server.persistence.api.ServerPlatformRepositories;
import cafe.jeffrey.server.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.server.persistence.api.ProjectRepository;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

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
    private final ServerPlatformRepositories platformRepositories;
    private final RepositoryStorage repositoryStorage;
    private final WorkspaceEventPublisher workspaceEventPublisher;
    private final Clock clock;
    private final ObjectFactory<SchedulerTrigger> projectsSynchronizerTrigger;
    private final InstanceEnvironmentParser instanceEnvironmentParser;

    public ServerProjectManager(
            Clock clock,
            ProjectInfo projectInfo,
            ObjectFactory<SchedulerTrigger> projectsSynchronizerTrigger,
            ServerPlatformRepositories platformRepositories,
            RepositoryStorage repositoryStorage,
            WorkspaceEventPublisher workspaceEventPublisher,
            InstanceEnvironmentParser instanceEnvironmentParser) {

        this.clock = clock;
        String projectId = projectInfo.id();
        this.projectsSynchronizerTrigger = projectsSynchronizerTrigger;
        this.projectInfo = projectInfo;
        this.projectRepository = platformRepositories.newProjectRepository(projectId);
        this.platformRepositories = platformRepositories;
        this.repositoryStorage = repositoryStorage;
        this.workspaceEventPublisher = workspaceEventPublisher;
        this.instanceEnvironmentParser = instanceEnvironmentParser;
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
                workspaceEventPublisher,
                instanceEnvironmentParser);
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
                projectInfo.deletedAt() != null);
    }

    @Override
    public void restore() {
        projectRepository.restore();
        LOG.info("Restored project: projectId={}", projectInfo.id());
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
