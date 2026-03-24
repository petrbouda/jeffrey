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

import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import pbouda.jeffrey.shared.common.model.repository.RecordingStatus;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;
import pbouda.jeffrey.local.core.manager.MessagesManager;
import pbouda.jeffrey.local.core.manager.ProfilerSettingsManager;
import pbouda.jeffrey.local.core.manager.ProfilesManager;
import pbouda.jeffrey.local.core.manager.RecordingsDownloadManager;
import pbouda.jeffrey.local.core.manager.RecordingsManager;
import pbouda.jeffrey.local.core.manager.RepositoryManager;
import java.util.function.Function;

public interface ProjectManager {

    record DetailedProjectInfo(
            ProjectInfo projectInfo,
            RecordingStatus status,
            int profileCount,
            int recordingCount,
            int sessionCount,
            RecordingEventSource eventSource,
            boolean isBlocked) {
    }

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, ProjectManager> {
    }

    ProfilesManager profilesManager();

    RecordingsManager recordingsManager();

    MessagesManager messagesManager();

    RecordingsDownloadManager recordingsDownloadManager();

    RepositoryManager repositoryManager();

    ProfilerSettingsManager profilerSettingsManager();

    RemoteInstancesManager instancesManager();

    ProjectInfo info();

    DetailedProjectInfo detailedInfo();

    void updateName(String name);

    void block();

    void unblock();

    void delete(WorkspaceEventCreator createdBy);
}
