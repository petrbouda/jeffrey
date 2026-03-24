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

package pbouda.jeffrey.local.core.manager.project;

import pbouda.jeffrey.local.core.LocalJeffreyDirs;
import pbouda.jeffrey.local.core.manager.*;
import pbouda.jeffrey.local.core.client.RemoteClients;
import pbouda.jeffrey.local.core.manager.workspace.RemoteRecordingsDownloadManager;
import pbouda.jeffrey.local.core.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.local.persistence.model.RemoteWorkspaceInfo;
import pbouda.jeffrey.local.persistence.repository.LocalCoreRepositories;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

public class RemoteProjectManager implements ProjectManager {

    private final LocalJeffreyDirs jeffreyDirs;
    private final RemoteWorkspaceInfo workspaceInfo;
    private final DetailedProjectInfo detailedProjectInfo;
    private final RemoteClients remoteClients;
    private final ProfilesManager.Factory profilesManagerFactory;
    private final ProjectRecordingInitializer.Factory recordingInitializerFactory;
    private final LocalCoreRepositories localCoreRepositories;

    public RemoteProjectManager(
            LocalJeffreyDirs jeffreyDirs,
            RemoteWorkspaceInfo workspaceInfo,
            DetailedProjectInfo detailedProjectInfo,
            RemoteClients remoteClients,
            ProfilesManager.Factory profilesManagerFactory,
            ProjectRecordingInitializer.Factory recordingInitializerFactory,
            LocalCoreRepositories localCoreRepositories) {

        this.jeffreyDirs = jeffreyDirs;
        this.workspaceInfo = workspaceInfo;
        this.detailedProjectInfo = detailedProjectInfo;
        this.remoteClients = remoteClients;
        this.profilesManagerFactory = profilesManagerFactory;
        this.recordingInitializerFactory = recordingInitializerFactory;
        this.localCoreRepositories = localCoreRepositories;
    }

    @Override
    public ProjectInfo info() {
        return detailedProjectInfo.projectInfo();
    }

    @Override
    public DetailedProjectInfo detailedInfo() {
        return detailedProjectInfo;
    }

    @Override
    public ProfilesManager profilesManager() {
        return profilesManagerFactory.apply(detailedProjectInfo.projectInfo());
    }

    @Override
    public RecordingsManager recordingsManager() {
        ProjectInfo projectInfo = detailedProjectInfo.projectInfo();
        return new RecordingsManagerImpl(
                projectInfo,
                recordingInitializerFactory.apply(projectInfo),
                localCoreRepositories.newRecordingRepository(projectInfo.id()));
    }

    @Override
    public MessagesManager messagesManager() {
        return new RemoteMessagesManager(
                remoteClients.messages(),
                workspaceInfo.id(),
                detailedProjectInfo.projectInfo().id());
    }

    @Override
    public RecordingsDownloadManager recordingsDownloadManager() {
        ProjectInfo projectInfo = detailedProjectInfo.projectInfo();
        ProjectRecordingInitializer recordingInitializer = recordingInitializerFactory.apply(projectInfo);

        return new RemoteRecordingsDownloadManager(
                jeffreyDirs,
                projectInfo,
                workspaceInfo,
                remoteClients.recordings(),
                remoteClients.repository(),
                recordingInitializer);
    }

    @Override
    public RepositoryManager repositoryManager() {
        return new RemoteRepositoryManager(
                jeffreyDirs,
                detailedProjectInfo.projectInfo(),
                workspaceInfo,
                remoteClients.repository(),
                remoteClients.recordings());
    }

    @Override
    public ProfilerSettingsManager profilerSettingsManager() {
        return new RemoteProfilerSettingsManager(
                remoteClients.profiler(),
                workspaceInfo,
                detailedProjectInfo.projectInfo().id());
    }

    @Override
    public RemoteInstancesManager instancesManager() {
        return new RemoteInstancesManager(
                detailedProjectInfo.projectInfo(),
                workspaceInfo,
                remoteClients.instances());
    }

    @Override
    public void updateName(String name) {
        throw new UnsupportedOperationException("Renaming remote projects is not supported");
    }

    @Override
    public void block() {
        remoteClients.projects().blockProject(
                workspaceInfo.id(),
                detailedProjectInfo.projectInfo().id());
    }

    @Override
    public void unblock() {
        remoteClients.projects().unblockProject(
                workspaceInfo.id(),
                detailedProjectInfo.projectInfo().id());
    }

    @Override
    public void delete(WorkspaceEventCreator createdBy) {
        remoteClients.projects().deleteProject(
                workspaceInfo.id(),
                detailedProjectInfo.projectInfo().id());
    }
}
