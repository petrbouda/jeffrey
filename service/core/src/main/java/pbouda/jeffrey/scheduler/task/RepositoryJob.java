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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.model.RepositoryInfo;
import pbouda.jeffrey.provider.api.model.JobInfo;
import pbouda.jeffrey.provider.api.model.JobType;

import java.util.List;
import java.util.Optional;

public abstract class RepositoryJob extends Job {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryJob.class);

    public RepositoryJob(ProjectsManager projectsManager, JobType jobType) {
        super(projectsManager, jobType);
    }

    @Override
    protected void execute(ProjectManager manager, List<JobInfo> jobInfos) {
        Optional<RepositoryInfo> repository = resolveRepository(manager);
        if (repository.isEmpty()) {
            LOG.warn("Repository was not registered: project='{}'", manager.info().id());
            return;
        }

        executeOnRepository(manager, repository.get(), jobInfos);
    }

    protected abstract void executeOnRepository(
            ProjectManager manager, RepositoryInfo repositoryInfo, List<JobInfo> jobInfo);

    private static Optional<RepositoryInfo> resolveRepository(ProjectManager projectManager) {
        return projectManager.repositoryManager().info();
    }
}
