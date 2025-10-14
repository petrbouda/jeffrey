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
import pbouda.jeffrey.common.model.RecordingEventSource;
import pbouda.jeffrey.common.model.repository.RecordingStatus;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.manager.RecordingsDownloadManager;
import pbouda.jeffrey.manager.RecordingsManager;
import pbouda.jeffrey.manager.RepositoryManager;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.manager.SettingsManager;
import pbouda.jeffrey.recording.ProjectRecordingInitializer;

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

    void initialize();

    ProfilesManager profilesManager();

    RecordingsManager recordingsManager();

    RecordingsDownloadManager recordingsDownloadManager();

    RepositoryManager repositoryManager();

    SchedulerManager schedulerManager();

    SettingsManager settingsManager();

    ProjectRecordingInitializer recordingInitializer();

    boolean isInitializing();

    ProjectInfo info();

    DetailedProjectInfo detailedInfo();

    void delete();
}
