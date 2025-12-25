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

package pbouda.jeffrey.manager.project;

import org.springframework.beans.factory.ObjectFactory;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.Recording;
import pbouda.jeffrey.common.model.RecordingEventSource;
import pbouda.jeffrey.common.model.job.JobInfo;
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.repository.RecordingStatus;
import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventCreator;
import pbouda.jeffrey.common.exception.Exceptions;
import pbouda.jeffrey.manager.*;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.provider.api.repository.ProjectRecordingRepository;
import pbouda.jeffrey.provider.api.repository.ProjectRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.provider.api.repository.SchedulerRepository;
import pbouda.jeffrey.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.workspace.WorkspaceEventConverter;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class CommonProjectManager implements ProjectManager {

    private final ProjectInfo projectInfo;
    private final ProjectRecordingInitializer recordingInitializer;
    private final ProjectRepository projectRepository;
    private final ProjectRecordingRepository recordingRepository;
    private final SchedulerRepository schedulerRepository;
    private final ProfilesManager.Factory profilesManagerFactory;
    private final Repositories repositories;
    private final RemoteRepositoryStorage.Factory remoteRepositoryStorageFactory;
    private final JobDescriptorFactory jobDescriptorFactory;
    private final CompositeWorkspacesManager compositeWorkspacesManager;
    private final Clock clock;
    private final ObjectFactory<Runnable> repositoryCompressionTrigger;
    private final ObjectFactory<Runnable> projectsSynchronizerTrigger;

    public CommonProjectManager(
            Clock clock,
            ProjectInfo projectInfo,
            ObjectFactory<Runnable> projectsSynchronizerTrigger,
            ObjectFactory<Runnable> repositoryCompressionTrigger,
            ProjectRecordingInitializer recordingInitializer,
            ProfilesManager.Factory profilesManagerFactory,
            Repositories repositories,
            RemoteRepositoryStorage.Factory remoteRepositoryStorageFactory,
            CompositeWorkspacesManager compositeWorkspacesManager,
            JobDescriptorFactory jobDescriptorFactory) {

        this.clock = clock;
        String projectId = projectInfo.id();
        this.projectsSynchronizerTrigger = projectsSynchronizerTrigger;
        this.repositoryCompressionTrigger = repositoryCompressionTrigger;
        this.projectInfo = projectInfo;
        this.recordingInitializer = recordingInitializer;
        this.projectRepository = repositories.newProjectRepository(projectId);
        this.recordingRepository = repositories.newProjectRecordingRepository(projectId);
        this.schedulerRepository = repositories.newProjectSchedulerRepository(projectId);
        this.profilesManagerFactory = profilesManagerFactory;
        this.repositories = repositories;
        this.remoteRepositoryStorageFactory = remoteRepositoryStorageFactory;
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
    public RecordingsDownloadManager recordingsDownloadManager() {
        return new RecordingsDownloadManagerImpl(
                projectInfo, recordingInitializer, repositoryManager(), repositoryCompressionTrigger.getObject());
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
                repositoryCompressionTrigger.getObject(),
                repositories.newProjectRepositoryRepository(projectInfo.id()),
                remoteRepositoryStorageFactory.apply(projectInfo),
                workspaceOpt.get());
    }

    @Override
    public SchedulerManager schedulerManager() {
        return new SchedulerManagerImpl(schedulerRepository, jobDescriptorFactory);
    }

    @Override
    public SettingsManager settingsManager() {
        return new SettingsManagerImpl(projectRepository);
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
        projectsSynchronizerTrigger.getObject().run();
    }
}
