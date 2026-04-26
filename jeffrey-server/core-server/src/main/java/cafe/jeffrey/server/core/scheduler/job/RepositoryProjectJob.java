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

package cafe.jeffrey.server.core.scheduler.job;

import cafe.jeffrey.server.core.manager.project.ProjectManager;
import cafe.jeffrey.server.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.server.core.project.repository.RepositoryStorage;
import cafe.jeffrey.server.core.scheduler.JobContext;
import cafe.jeffrey.server.core.scheduler.job.descriptor.JobDescriptor;
import cafe.jeffrey.server.core.scheduler.job.descriptor.JobDescriptorFactory;

/**
 * Base class for PROJECT-level jobs that operate on repository storage.
 * Extends ProjectJob and provides access to RemoteRepositoryStorage.
 */
public abstract class RepositoryProjectJob<T extends JobDescriptor<T>> extends ProjectJob<T> {

    private final RepositoryStorage.Factory remoteRepositoryManagerFactory;

    protected RepositoryProjectJob(
            WorkspacesManager workspacesManager,
            RepositoryStorage.Factory remoteRepositoryManagerFactory,
            JobDescriptorFactory jobDescriptorFactory) {

        super(workspacesManager, jobDescriptorFactory);
        this.remoteRepositoryManagerFactory = remoteRepositoryManagerFactory;
    }

    @Override
    protected void execute(ProjectManager manager, T jobDescriptor, JobContext context) {
        RepositoryStorage repositoryStorage = remoteRepositoryManagerFactory.apply(manager.info());
        executeOnRepository(manager, repositoryStorage, jobDescriptor, context);
    }

    protected abstract void executeOnRepository(
            ProjectManager manager, RepositoryStorage repositoryStorage, T jobDescriptor, JobContext context);
}
