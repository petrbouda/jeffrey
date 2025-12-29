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
import pbouda.jeffrey.shared.model.ProjectInfo;
import pbouda.jeffrey.shared.model.RecordingEventSource;
import pbouda.jeffrey.shared.model.repository.RecordingStatus;
import pbouda.jeffrey.shared.model.workspace.WorkspaceEventCreator;
import pbouda.jeffrey.platform.manager.ProfilesManager;
import pbouda.jeffrey.platform.manager.RecordingsDownloadManager;
import pbouda.jeffrey.platform.manager.RecordingsManager;
import pbouda.jeffrey.platform.manager.RepositoryManager;
import pbouda.jeffrey.platform.manager.SchedulerManager;
import pbouda.jeffrey.platform.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.provider.api.repository.ProjectRepository;

import java.util.function.Function;

public interface ProjectManager {

    record DetailedProjectInfo(
            ProjectInfo projectInfo,
            RecordingStatus status,
            int profileCount,
            int recordingCount,
            int sessionCount,
            int jobCount,
            int alertCount,
            RecordingEventSource eventSource,
            boolean isVirtual,
            boolean isOrphaned) {

        public DetailedProjectInfo orphaned() {
            return new DetailedProjectInfo(
                    projectInfo,
                    status,
                    profileCount,
                    recordingCount,
                    sessionCount,
                    jobCount,
                    alertCount,
                    eventSource,
                    isVirtual,
                    true);
        }
    }

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, ProjectManager> {
    }

    ProfilesManager profilesManager();

    RecordingsManager recordingsManager();

    RecordingsDownloadManager recordingsDownloadManager();

    RepositoryStorage repositoryStorage();

    RepositoryManager repositoryManager();

    SchedulerManager schedulerManager();

    ProjectRepository projectRepository();

    ProjectRecordingInitializer recordingInitializer();

    boolean isInitializing();

    ProjectInfo info();

    DetailedProjectInfo detailedInfo();

    void delete(WorkspaceEventCreator createdBy);
}
