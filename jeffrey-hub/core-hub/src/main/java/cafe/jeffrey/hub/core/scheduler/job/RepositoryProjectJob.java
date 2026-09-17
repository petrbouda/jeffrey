/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
