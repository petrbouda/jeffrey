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

import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.shared.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.model.ProjectInfo;
import pbouda.jeffrey.shared.model.workspace.WorkspaceEventCreator;
import pbouda.jeffrey.shared.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.platform.manager.ProfilesManager;
import pbouda.jeffrey.platform.manager.RecordingsDownloadManager;
import pbouda.jeffrey.platform.manager.RecordingsManager;
import pbouda.jeffrey.platform.manager.RepositoryManager;
import pbouda.jeffrey.platform.manager.SchedulerManager;
import pbouda.jeffrey.platform.manager.SchedulerManagerImpl;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteRecordingsDownloadManager;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteWorkspaceClient;
import pbouda.jeffrey.platform.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.provider.api.repository.ProjectRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.provider.api.repository.SchedulerRepository;

import java.util.Optional;

public class RemoteProjectManager implements ProjectManager {

    private final JeffreyDirs jeffreyDirs;
    private final WorkspaceInfo workspaceInfo;
    private final DetailedProjectInfo detailedProjectInfo;
    private final Optional<ProjectManager> commonProjectManager;
    private final RemoteWorkspaceClient remoteWorkspaceClient;
    private final JobDescriptorFactory jobDescriptorFactory;
    private final SchedulerRepository schedulerRepository;

    public RemoteProjectManager(
            JeffreyDirs jeffreyDirs,
            WorkspaceInfo workspaceInfo,
            DetailedProjectInfo detailedProjectInfo,
            Optional<ProjectManager> commonProjectManager,
            RemoteWorkspaceClient remoteWorkspaceClient,
            Repositories repositories,
            JobDescriptorFactory jobDescriptorFactory) {

        this.jeffreyDirs = jeffreyDirs;
        this.workspaceInfo = workspaceInfo;
        this.detailedProjectInfo = detailedProjectInfo;
        this.commonProjectManager = commonProjectManager;
        this.remoteWorkspaceClient = remoteWorkspaceClient;
        this.jobDescriptorFactory = jobDescriptorFactory;
        this.schedulerRepository = repositories.newProjectSchedulerRepository(detailedProjectInfo.projectInfo().id());
    }

    @Override
    public ProjectInfo info() {
        return detailedProjectInfo.projectInfo();
    }

    @Override
    public DetailedProjectInfo detailedInfo() {
        return detailedProjectInfo;
    }

    private ProjectManager resolveProjectManager() {
        return commonProjectManager.orElseThrow(() ->
                new IllegalStateException("Common project manager was not found for remote project"));
    }

    @Override
    public ProfilesManager profilesManager() {
        return resolveProjectManager().profilesManager();
    }

    @Override
    public RecordingsManager recordingsManager() {
        return resolveProjectManager().recordingsManager();
    }

    @Override
    public RecordingsDownloadManager recordingsDownloadManager() {
        RecordingsDownloadManager recordingsDownloadManager = resolveProjectManager().recordingsDownloadManager();
        return new RemoteRecordingsDownloadManager(
                jeffreyDirs,
                detailedProjectInfo.projectInfo(),
                workspaceInfo,
                remoteWorkspaceClient,
                recordingsDownloadManager);
    }

    @Override
    public RepositoryStorage repositoryStorage() {
        throw new UnsupportedOperationException(
                "Repository storage is not supported in " + RemoteProjectManager.class.getSimpleName());
    }

    @Override
    public RepositoryManager repositoryManager() {
        return new RemoteRepositoryManager(detailedProjectInfo.projectInfo(), workspaceInfo, remoteWorkspaceClient);
    }

    @Override
    public SchedulerManager schedulerManager() {
        return new SchedulerManagerImpl(schedulerRepository, jobDescriptorFactory);
    }

    @Override
    public ProjectRepository projectRepository() {
        return resolveProjectManager().projectRepository();
    }

    @Override
    public ProjectRecordingInitializer recordingInitializer() {
        return resolveProjectManager().recordingInitializer();
    }

    @Override
    public boolean isInitializing() {
        return resolveProjectManager().isInitializing();
    }

    @Override
    public void delete(WorkspaceEventCreator createdBy) {
        resolveProjectManager().delete(createdBy);
    }
}
