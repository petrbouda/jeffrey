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

import cafe.jeffrey.hub.core.manager.HubRepositoryManager;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.project.repository.FilesystemRepositoryStorage;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.volume.ProjectDirectory;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.liveness.SessionLiveness;

public class HubProjectManager implements ProjectManager {

    private final ProjectInfo projectInfo;
    private final RepositoryStorage repositoryStorage;

    public HubProjectManager(ProjectDirectory project, FilesystemVolume volume, SessionLiveness liveness) {
        this.projectInfo = project.info();
        this.repositoryStorage = new FilesystemRepositoryStorage(project.info(), project.tree(), volume, liveness);
    }

    @Override
    public RepositoryStorage repositoryStorage() {
        return repositoryStorage;
    }

    @Override
    public RepositoryManager repositoryManager() {
        return new HubRepositoryManager(projectInfo, repositoryStorage);
    }

    @Override
    public ProjectInfo info() {
        return projectInfo;
    }
}
