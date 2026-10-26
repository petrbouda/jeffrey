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
import cafe.jeffrey.hub.core.volume.ProjectDirectory;
import cafe.jeffrey.hub.model.ProjectInfo;

import java.util.function.Function;

/**
 * One project directory of a workspace. The hub only collects: no profile analysis, recording
 * management or downloads of its own.
 */
public interface ProjectManager {

    @FunctionalInterface
    interface Factory extends Function<ProjectDirectory, ProjectManager> {
    }

    RepositoryStorage repositoryStorage();

    RepositoryManager repositoryManager();

    ProjectInfo info();

}
