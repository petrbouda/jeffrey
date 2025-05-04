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

package pbouda.jeffrey.scheduler.task;

import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.provider.api.model.JobInfo;
import pbouda.jeffrey.provider.api.model.JobType;

public abstract class RepositoryJob extends Job {

    private final RemoteRepositoryStorage.Factory remoteRepositoryManagerFactory;

    public RepositoryJob(
            ProjectsManager projectsManager,
            RemoteRepositoryStorage.Factory remoteRepositoryManagerFactory,
            JobType jobType) {

        super(projectsManager, jobType);
        this.remoteRepositoryManagerFactory = remoteRepositoryManagerFactory;
    }

    @Override
    protected void execute(ProjectManager manager, JobInfo jobInfo) {
        RemoteRepositoryStorage remoteRepositoryStorage = remoteRepositoryManagerFactory.apply(jobInfo.projectId());
        executeOnRepository(manager, remoteRepositoryStorage, jobInfo);
    }

    protected abstract void executeOnRepository(
            ProjectManager manager, RemoteRepositoryStorage remoteRepositoryStorage, JobInfo jobInfo);
}
