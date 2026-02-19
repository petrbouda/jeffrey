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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import pbouda.jeffrey.platform.manager.*;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.platform.queue.PersistentQueue;
import pbouda.jeffrey.platform.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.platform.scheduler.SchedulerTrigger;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.platform.workspace.WorkspaceEventConverter;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.provider.platform.repository.ProjectInstanceRepository;
import pbouda.jeffrey.provider.platform.repository.ProjectRecordingRepository;
import pbouda.jeffrey.provider.platform.repository.ProjectRepository;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.provider.platform.repository.SchedulerRepository;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.Recording;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import pbouda.jeffrey.shared.common.model.job.JobInfo;
import pbouda.jeffrey.shared.common.model.repository.RecordingSession;
import pbouda.jeffrey.shared.common.model.repository.RecordingStatus;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;

public class CommonProjectManager implements ProjectManager {

    private static final Logger LOG = LoggerFactory.getLogger(CommonProjectManager.class);

    private final ProjectInfo projectInfo;
    private final ProjectRecordingInitializer recordingInitializer;
    private final ProjectRepository projectRepository;
    private final ProjectRecordingRepository recordingRepository;
    private final SchedulerRepository schedulerRepository;
    private final ProfilesManager.Factory profilesManagerFactory;
    private final PlatformRepositories platformRepositories;
    private final RepositoryStorage repositoryStorage;
    private final JobDescriptorFactory jobDescriptorFactory;
    private final PersistentQueue<WorkspaceEvent> workspaceEventQueue;
    private final Clock clock;
    private final ObjectFactory<SchedulerTrigger> projectsSynchronizerTrigger;

    public CommonProjectManager(
            Clock clock,
            ProjectInfo projectInfo,
            ObjectFactory<SchedulerTrigger> projectsSynchronizerTrigger,
            ProjectRecordingInitializer recordingInitializer,
            ProfilesManager.Factory profilesManagerFactory,
            PlatformRepositories platformRepositories,
            RepositoryStorage repositoryStorage,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue,
            JobDescriptorFactory jobDescriptorFactory) {

        this.clock = clock;
        String projectId = projectInfo.id();
        this.projectsSynchronizerTrigger = projectsSynchronizerTrigger;
        this.projectInfo = projectInfo;
        this.recordingInitializer = recordingInitializer;
        this.projectRepository = platformRepositories.newProjectRepository(projectId);
        this.recordingRepository = platformRepositories.newProjectRecordingRepository(projectId);
        this.schedulerRepository = platformRepositories.newProjectSchedulerRepository(projectId);
        this.profilesManagerFactory = profilesManagerFactory;
        this.platformRepositories = platformRepositories;
        this.repositoryStorage = repositoryStorage;
        this.workspaceEventQueue = workspaceEventQueue;
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
        return new MessagesManagerImpl(
                clock,
                platformRepositories.newMessageRepository(projectInfo.id()),
                platformRepositories.newAlertRepository(projectInfo.id()));
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
        return new RepositoryManagerImpl(
                clock,
                projectInfo,
                projectsSynchronizerTrigger.getObject(),
                platformRepositories.newProjectRepositoryRepository(projectInfo.id()),
                repositoryStorage,
                workspaceEventQueue);
    }

    @Override
    public SchedulerManager schedulerManager() {
        return new SchedulerManagerImpl(schedulerRepository, jobDescriptorFactory);
    }

    @Override
    public ProfilerSettingsManager profilerSettingsManager() {
        return new LiveProfilerSettingsManager(
                platformRepositories.newProfilerRepository(),
                projectInfo.workspaceId(),
                projectInfo.id());
    }

    @Override
    public ProjectRepository projectRepository() {
        return projectRepository;
    }

    @Override
    public ProjectInstanceRepository projectInstanceRepository() {
        return platformRepositories.newProjectInstanceRepository(projectInfo.id());
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
        LOG.debug("Deleting project: projectId={}", info().id());

        WorkspaceEvent workspaceEvent = WorkspaceEventConverter.projectDeleted(
                clock.instant(),
                projectInfo.workspaceId(),
                projectInfo.id(),
                createdBy);

        workspaceEventQueue.appendBatch(projectInfo.workspaceId(), List.of(workspaceEvent));

        // Trigger event synchronization
        projectsSynchronizerTrigger.getObject().execute();
    }
}
