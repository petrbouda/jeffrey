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

package cafe.jeffrey.hub.core.manager.project;

import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.repository.RecordingStatus;

import java.util.function.Function;

/**
 * Hub-specific project manager — lean version without profile analysis,
 * recording management, or download capabilities. The hub is a pure collector.
 */
public interface ProjectManager {

    /**
     * @param status ACTIVE while any session of the project is still recording, FINISHED once
     *               every session has finished, {@code null} for a project with no session yet
     */
    record DetailedProjectInfo(
            ProjectInfo projectInfo,
            RecordingStatus status,
            int sessionCount) {
    }

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, ProjectManager> {
    }

    RepositoryStorage repositoryStorage();

    RepositoryManager repositoryManager();


    ProjectInstanceRepository projectInstanceRepository();

    ProjectInfo info();

    DetailedProjectInfo detailedInfo();

    void restore();

    void delete();
}
