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

package cafe.jeffrey.local.core.manager.project;

import cafe.jeffrey.local.core.manager.EventStreamingManager;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;
import cafe.jeffrey.local.core.manager.ProfilerSettingsManager;
import cafe.jeffrey.local.core.manager.ProfilesManager;
import cafe.jeffrey.local.core.manager.RecordingsDownloadManager;
import cafe.jeffrey.local.core.manager.RecordingsManager;
import cafe.jeffrey.local.core.manager.RepositoryManager;
import java.util.function.Function;

public interface ProjectManager {

    record DetailedProjectInfo(
            ProjectInfo projectInfo,
            RecordingStatus status,
            int profileCount,
            int recordingCount,
            int sessionCount,
            RecordingEventSource eventSource,
            boolean isDeleted) {
    }

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, ProjectManager> {
    }

    ProfilesManager profilesManager();

    RecordingsManager recordingsManager();

    RecordingsDownloadManager recordingsDownloadManager();

    RepositoryManager repositoryManager();

    ProfilerSettingsManager profilerSettingsManager();

    RemoteInstancesManager instancesManager();

    ProjectInfo info();

    DetailedProjectInfo detailedInfo();

    void updateName(String name);

    void restore();

    void delete(WorkspaceEventCreator createdBy);

    /**
     * Returns the event streaming manager for subscribing to live JFR events
     * from the remote server. Only available for remote workspace projects.
     *
     * @return the event streaming manager, or null for local projects
     */
    default EventStreamingManager eventStreamingManager() {
        return null;
    }
}
