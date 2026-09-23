/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.hub.core.scheduler.job;

import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;

/**
 * Base class for project jobs that work on the project's repository storage — the one the
 * project's manager already holds, rather than a second one built per project per tick.
 */
public abstract class RepositoryProjectJob extends ProjectJob {

    protected RepositoryProjectJob(WorkspacesManager workspacesManager) {
        super(workspacesManager);
    }

    @Override
    protected void execute(ProjectManager manager) {
        executeOnRepository(manager, manager.repositoryStorage());
    }

    protected abstract void executeOnRepository(ProjectManager manager, RepositoryStorage repositoryStorage);
}
