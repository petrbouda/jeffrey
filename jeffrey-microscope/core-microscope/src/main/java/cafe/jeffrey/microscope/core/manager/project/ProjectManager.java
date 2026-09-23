/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.manager.project;

import cafe.jeffrey.hub.client.manager.RemoteInstancesManager;

import cafe.jeffrey.microscope.model.ProjectInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.model.repository.RecordingStatus;
import cafe.jeffrey.microscope.core.manager.ProfilesManager;
import cafe.jeffrey.recordings.core.RecordingsDownloadManager;
import cafe.jeffrey.hub.client.manager.RepositoryManager;
import java.util.function.Function;

public interface ProjectManager {

    record DetailedProjectInfo(
            ProjectInfo projectInfo,
            RecordingStatus status,
            int sessionCount,
            RecordingEventSource eventSource,
            boolean isDeleted) {
    }

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, ProjectManager> {
    }

    ProfilesManager profilesManager();

    RecordingsDownloadManager recordingsDownloadManager();

    RepositoryManager repositoryManager();

    RemoteInstancesManager instancesManager();

    ProjectInfo info();

    DetailedProjectInfo detailedInfo();

    void updateName(String name);

    void restore();

    void delete();
}
