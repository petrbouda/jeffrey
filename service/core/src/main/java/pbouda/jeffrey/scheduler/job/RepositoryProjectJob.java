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

package pbouda.jeffrey.scheduler.job;

import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptor;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;

import java.time.Duration;

public abstract class RepositoryProjectJob<T extends JobDescriptor<T>> extends ProjectJob<T> {

    private final RemoteRepositoryStorage.Factory remoteRepositoryManagerFactory;

    public RepositoryProjectJob(
            ProjectsManager projectsManager,
            RemoteRepositoryStorage.Factory remoteRepositoryManagerFactory,
            JobDescriptorFactory jobDescriptorFactory,
            JobType jobType,
            Duration period) {

        super(projectsManager, jobDescriptorFactory, jobType, period);
        this.remoteRepositoryManagerFactory = remoteRepositoryManagerFactory;
    }

    @Override
    protected void execute(ProjectManager manager, T jobDescriptor) {
        RemoteRepositoryStorage remoteRepositoryStorage = remoteRepositoryManagerFactory.apply(manager.info().id());
        executeOnRepository(manager, remoteRepositoryStorage, jobDescriptor);
    }

    protected abstract void executeOnRepository(
            ProjectManager manager, RemoteRepositoryStorage remoteRepositoryStorage, T jobDescriptor);
}
