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

import pbouda.jeffrey.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventCreator;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.manager.RecordingsDownloadManager;
import pbouda.jeffrey.manager.RecordingsManager;
import pbouda.jeffrey.manager.RepositoryManager;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.manager.SettingsManager;
import pbouda.jeffrey.manager.workspace.remote.RemoteRecordingsDownloadManager;
import pbouda.jeffrey.manager.workspace.remote.RemoteWorkspaceClient;
import pbouda.jeffrey.recording.ProjectRecordingInitializer;

import java.util.Optional;

public class RemoteProjectManager implements ProjectManager {

    private final JeffreyDirs jeffreyDirs;
    private final WorkspaceInfo workspaceInfo;
    private final DetailedProjectInfo detailedProjectInfo;
    private final Optional<ProjectManager> commonProjectManager;
    private final RemoteWorkspaceClient remoteWorkspaceClient;

    private static final String UNSUPPORTED =
            "Not supported operation in " + RemoteProjectManager.class.getSimpleName();

    public RemoteProjectManager(
            JeffreyDirs jeffreyDirs,
            WorkspaceInfo workspaceInfo,
            DetailedProjectInfo detailedProjectInfo,
            Optional<ProjectManager> commonProjectManager,
            RemoteWorkspaceClient remoteWorkspaceClient) {

        this.jeffreyDirs = jeffreyDirs;
        this.workspaceInfo = workspaceInfo;
        this.detailedProjectInfo = detailedProjectInfo;
        this.commonProjectManager = commonProjectManager;
        this.remoteWorkspaceClient = remoteWorkspaceClient;
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
    public void initialize() {
        resolveProjectManager().initialize();
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
    public RepositoryManager repositoryManager() {
        return new RemoteRepositoryManager(detailedProjectInfo.projectInfo(), workspaceInfo, remoteWorkspaceClient);
    }

    @Override
    public SchedulerManager schedulerManager() {
        // Disabled for REMOTE Workspace
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public SettingsManager settingsManager() {
        return resolveProjectManager().settingsManager();
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
