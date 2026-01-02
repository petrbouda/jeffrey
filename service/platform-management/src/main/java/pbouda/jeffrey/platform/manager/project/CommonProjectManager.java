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

package pbouda.jeffrey.platform.manager.project;

import org.springframework.beans.factory.ObjectFactory;
import pbouda.jeffrey.platform.manager.*;
import pbouda.jeffrey.platform.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.platform.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.platform.scheduler.SchedulerTrigger;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.platform.workspace.WorkspaceEventConverter;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.provider.api.repository.ProjectRecordingRepository;
import pbouda.jeffrey.provider.api.repository.ProjectRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.provider.api.repository.SchedulerRepository;
import pbouda.jeffrey.shared.exception.Exceptions;
import pbouda.jeffrey.shared.model.ProfileInfo;
import pbouda.jeffrey.shared.model.ProjectInfo;
import pbouda.jeffrey.shared.model.Recording;
import pbouda.jeffrey.shared.model.RecordingEventSource;
import pbouda.jeffrey.shared.model.job.JobInfo;
import pbouda.jeffrey.shared.model.repository.RecordingSession;
import pbouda.jeffrey.shared.model.repository.RecordingStatus;
import pbouda.jeffrey.shared.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.model.workspace.WorkspaceEventCreator;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class CommonProjectManager implements ProjectManager {

    private final ProjectInfo projectInfo;
    private final ProjectRecordingInitializer recordingInitializer;
    private final ProjectRepository projectRepository;
    private final ProjectRecordingRepository recordingRepository;
    private final SchedulerRepository schedulerRepository;
    private final ProfilesManager.Factory profilesManagerFactory;
    private final Repositories repositories;
    private final RepositoryStorage repositoryStorage;
    private final JobDescriptorFactory jobDescriptorFactory;
    private final CompositeWorkspacesManager compositeWorkspacesManager;
    private final Clock clock;
    private final ObjectFactory<SchedulerTrigger> projectsSynchronizerTrigger;

    public CommonProjectManager(
            Clock clock,
            ProjectInfo projectInfo,
            ObjectFactory<SchedulerTrigger> projectsSynchronizerTrigger,
            ProjectRecordingInitializer recordingInitializer,
            ProfilesManager.Factory profilesManagerFactory,
            Repositories repositories,
            RepositoryStorage repositoryStorage,
            CompositeWorkspacesManager compositeWorkspacesManager,
            JobDescriptorFactory jobDescriptorFactory) {

        this.clock = clock;
        String projectId = projectInfo.id();
        this.projectsSynchronizerTrigger = projectsSynchronizerTrigger;
        this.projectInfo = projectInfo;
        this.recordingInitializer = recordingInitializer;
        this.projectRepository = repositories.newProjectRepository(projectId);
        this.recordingRepository = repositories.newProjectRecordingRepository(projectId);
        this.schedulerRepository = repositories.newProjectSchedulerRepository(projectId);
        this.profilesManagerFactory = profilesManagerFactory;
        this.repositories = repositories;
        this.repositoryStorage = repositoryStorage;
        this.compositeWorkspacesManager = compositeWorkspacesManager;
        this.jobDescriptorFactory = jobDescriptorFactory;
    }

    @Override
    public ProfilesManager profilesManager() {
        return profilesManagerFactory.apply(projectInfo);
    }

    @Override
    public RecordingsManager recordingsManager() {
        return new RecordingsManagerImpl(projectInfo, recordingInitializer, recordingRepository);
    }

    @Override
    public MessagesManager messagesManager() {
        return new MessagesManagerImpl(clock, repositoryStorage);
    }

    @Override
    public RecordingsDownloadManager recordingsDownloadManager() {
        return new RecordingsDownloadManagerImpl(recordingInitializer, repositoryStorage);
    }

    @Override
    public RepositoryStorage repositoryStorage() {
        return repositoryStorage;
    }

    @Override
    public RepositoryManager repositoryManager() {
        Optional<WorkspaceManager> workspaceOpt = compositeWorkspacesManager.findById(projectInfo.workspaceId());
        if (workspaceOpt.isEmpty()) {
            throw Exceptions.workspaceNotFound(projectInfo.workspaceId());
        }

        return new RepositoryManagerImpl(
                clock,
                projectInfo,
                projectsSynchronizerTrigger.getObject(),
                repositories.newProjectRepositoryRepository(projectInfo.id()),
                repositoryStorage,
                workspaceOpt.get());
    }

    @Override
    public SchedulerManager schedulerManager() {
        return new SchedulerManagerImpl(schedulerRepository, jobDescriptorFactory);
    }

    @Override
    public ProfilerSettingsManager profilerSettingsManager() {
        return new LiveProfilerSettingsManager(
                repositories.newProfilerRepository(),
                projectInfo.workspaceId(),
                projectInfo.id());
    }

    @Override
    public ProjectRepository projectRepository() {
        return projectRepository;
    }

    @Override
    public ProjectRecordingInitializer recordingInitializer() {
        return recordingInitializer;
    }

    @Override
    public boolean isInitializing() {
        return profilesManager().allProfiles().stream()
                .anyMatch(profile -> !profile.info().enabled());
    }

    @Override
    public ProjectInfo info() {
        return projectInfo;
    }

    @Override
    public DetailedProjectInfo detailedInfo() {
        List<Recording> allRecordings = recordingsManager().all();
        List<JobInfo> allJobs = schedulerManager().all();

        var allProfiles = profilesManager().allProfiles();
        var latestProfile = allProfiles.stream()
                .max(Comparator.comparing(p -> p.info().createdAt()))
                .map(ProfileManager::info);

        List<RecordingSession> recordingSessions = repositoryManager()
                .listRecordingSessions(false);

        RecordingStatus recordingStatus = recordingSessions.stream()
                .limit(1)
                .findAny()
                .map(RecordingSession::status).orElse(null);

        return new DetailedProjectInfo(
                projectInfo,
                recordingStatus,
                allProfiles.size(),
                allRecordings.size(),
                recordingSessions.size(),
                allJobs.size(),
                0,
                latestProfile.map(ProfileInfo::eventSource).orElse(RecordingEventSource.UNKNOWN),
                false,
                false);
    }

    @Override
    public void delete(WorkspaceEventCreator createdBy) {
        Optional<WorkspaceManager> workspaceOpt = compositeWorkspacesManager.findById(projectInfo.workspaceId());
        if (workspaceOpt.isEmpty()) {
            throw Exceptions.workspaceNotFound(projectInfo.workspaceId());
        }

        WorkspaceEvent workspaceEvent = WorkspaceEventConverter.projectDeleted(
                clock.instant(),
                projectInfo.workspaceId(),
                projectInfo.id(),
                createdBy);

        workspaceOpt.get()
                .workspaceEventManager()
                .batchInsertEvents(List.of(workspaceEvent));

        // Trigger event synchronization
        projectsSynchronizerTrigger.getObject().execute();
    }
}
