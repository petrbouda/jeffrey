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

import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.Recording;
import pbouda.jeffrey.common.model.RecordingEventSource;
import pbouda.jeffrey.common.model.job.JobInfo;
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.repository.RecordingStatus;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.manager.RecordingsDownloadManager;
import pbouda.jeffrey.manager.RecordingsDownloadManagerImpl;
import pbouda.jeffrey.manager.RecordingsManager;
import pbouda.jeffrey.manager.RecordingsManagerImpl;
import pbouda.jeffrey.manager.RepositoryManager;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.manager.SchedulerManagerImpl;
import pbouda.jeffrey.manager.SettingsManager;
import pbouda.jeffrey.manager.SettingsManagerImpl;
import pbouda.jeffrey.provider.api.repository.ProjectRecordingRepository;
import pbouda.jeffrey.provider.api.repository.ProjectRepository;
import pbouda.jeffrey.provider.api.repository.SchedulerRepository;
import pbouda.jeffrey.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;

import java.util.Comparator;
import java.util.List;

public class CommonProjectManager implements ProjectManager {

    private final ProjectInfo projectInfo;
    private final ProjectRecordingInitializer recordingInitializer;
    private final ProjectRepository projectRepository;
    private final ProjectRecordingRepository recordingRepository;
    private final SchedulerRepository schedulerRepository;
    private final RepositoryManager repositoryManager;
    private final ProfilesManager.Factory profilesManagerFactory;
    private final JobDescriptorFactory jobDescriptorFactory;

    public CommonProjectManager(
            ProjectInfo projectInfo,
            ProjectRecordingInitializer recordingInitializer,
            ProjectRepository projectRepository,
            ProjectRecordingRepository RecordingRepository,
            SchedulerRepository schedulerRepository,
            RepositoryManager repositoryManager,
            ProfilesManager.Factory profilesManagerFactory,
            JobDescriptorFactory jobDescriptorFactory) {

        this.projectInfo = projectInfo;
        this.recordingInitializer = recordingInitializer;
        this.projectRepository = projectRepository;
        this.recordingRepository = RecordingRepository;
        this.schedulerRepository = schedulerRepository;
        this.repositoryManager = repositoryManager;
        this.profilesManagerFactory = profilesManagerFactory;
        this.jobDescriptorFactory = jobDescriptorFactory;
    }

    @Override
    public void initialize() {
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
        return new RecordingsDownloadManagerImpl(projectInfo, recordingInitializer, repositoryManager);
    }

    @Override
    public RepositoryManager repositoryManager() {
        return this.repositoryManager;
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
    public void delete() {
        profilesManager().allProfiles()
                .forEach(ProfileManager::delete);

        projectRepository.delete();
    }
}
