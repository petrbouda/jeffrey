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
import pbouda.jeffrey.model.JobInfo;
import pbouda.jeffrey.model.JobType;
import pbouda.jeffrey.model.RepositoryInfo;

import java.util.List;
import java.util.Optional;

public class RepositoryCleanerJob extends Job {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryCleanerJob.class);
    private static final JobType JOB_TYPE = JobType.REPOSITORY_CLEANER;

    public RepositoryCleanerJob(ProjectsManager projectsManager) {
        super(projectsManager, JOB_TYPE);
    }

    @Override
    protected void execute(ProjectManager manager, List<JobInfo> jobInfo) {
        Optional<RepositoryInfo> repository = resolveRepository(manager);
        if (repository.isEmpty()) {
            LOG.warn("Repository was not registered: project={}", manager.info().id());
            return;
        }

        // TODO: Cleaning the repository

        LOG.info("Cleaning the repository: {}", repository.get().repositoryPath());
    }

    private static Optional<RepositoryInfo> resolveRepository(ProjectManager projectManager) {
        return projectManager.repositoryManager().info();
    }
}
