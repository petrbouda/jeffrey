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
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.manager.RecordingsManager;
import pbouda.jeffrey.manager.RepositoryManager;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.manager.SettingsManager;
import pbouda.jeffrey.manager.workspace.remote.RemoteWorkspaceClient;
import pbouda.jeffrey.recording.ProjectRecordingInitializer;

public class RemoteProjectManager implements ProjectManager {

    private final DetailedProjectInfo detailedProjectInfo;
    private final RemoteWorkspaceClient remoteWorkspaceClient;

    private static final String UNSUPPORTED = "Not supported operation in remote project manager";

    public RemoteProjectManager(
            DetailedProjectInfo detailedProjectInfo,
            RemoteWorkspaceClient remoteWorkspaceClient) {

        this.detailedProjectInfo = detailedProjectInfo;
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
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public ProfilesManager profilesManager() {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public RecordingsManager recordingsManager() {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public RepositoryManager repositoryManager() {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public SchedulerManager schedulerManager() {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public SettingsManager settingsManager() {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public ProjectRecordingInitializer recordingInitializer() {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public ProjectSessionManager sessionManager() {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public boolean isInitializing() {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public void delete() {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }
}
