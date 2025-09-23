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

import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceSessionInfo;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.manager.RecordingsManager;
import pbouda.jeffrey.manager.RecordingsManagerImpl;
import pbouda.jeffrey.manager.RepositoryManager;
import pbouda.jeffrey.manager.RepositoryManagerImpl;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.manager.SchedulerManagerImpl;
import pbouda.jeffrey.manager.SettingsManager;
import pbouda.jeffrey.manager.SettingsManagerImpl;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.provider.api.repository.ProjectRecordingRepository;
import pbouda.jeffrey.provider.api.repository.ProjectRepository;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.provider.api.repository.SchedulerRepository;
import pbouda.jeffrey.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;

public class ProjectManagerImpl implements ProjectManager {

    private final ProjectInfo projectInfo;
    private final ProjectRecordingInitializer recordingInitializer;
    private final ProjectRepository projectRepository;
    private final ProjectRecordingRepository recordingRepository;
    private final ProjectRepositoryRepository repositoryRepository;
    private final SchedulerRepository schedulerRepository;
    private final ProfilesManager.Factory profilesManagerFactory;
    private final RemoteRepositoryStorage remoteRepositoryStorage;
    private final JobDescriptorFactory jobDescriptorFactory;

    public ProjectManagerImpl(
            ProjectInfo projectInfo,
            ProjectRecordingInitializer recordingInitializer,
            ProjectRepository projectRepository,
            ProjectRecordingRepository RecordingRepository,
            ProjectRepositoryRepository repositoryRepository,
            SchedulerRepository schedulerRepository,
            ProfilesManager.Factory profilesManagerFactory,
            RemoteRepositoryStorage remoteRepositoryStorage,
            JobDescriptorFactory jobDescriptorFactory) {

        this.projectInfo = projectInfo;
        this.recordingInitializer = recordingInitializer;
        this.projectRepository = projectRepository;
        this.recordingRepository = RecordingRepository;
        this.repositoryRepository = repositoryRepository;
        this.schedulerRepository = schedulerRepository;
        this.profilesManagerFactory = profilesManagerFactory;
        this.remoteRepositoryStorage = remoteRepositoryStorage;
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
        return new RecordingsManagerImpl(
                projectInfo,
                recordingInitializer,
                recordingRepository,
                repositoryManager());
    }

    @Override
    public RepositoryManager repositoryManager() {
        return new RepositoryManagerImpl(repositoryRepository, remoteRepositoryStorage);
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
    public ProjectSessionManager sessionManager() {
        return new ProjectSessionManagerImpl(projectRepository);
    }

    @Override
    public ProjectInfo info() {
        return projectInfo;
    }

    @Override
    public void delete() {
        profilesManager().allProfiles()
                .forEach(ProfileManager::delete);

        projectRepository.delete();
    }
}
