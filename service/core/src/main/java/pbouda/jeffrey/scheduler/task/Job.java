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
import pbouda.jeffrey.provider.api.model.JobInfo;
import pbouda.jeffrey.provider.api.model.JobType;

import java.util.List;

public abstract class Job implements Runnable {

    private final ProjectsManager projectsManager;
    private final JobType jobType;

    public Job(ProjectsManager projectsManager, JobType jobType) {
        this.projectsManager = projectsManager;
        this.jobType = jobType;
    }

    @Override
    public void run() {
        for (ProjectManager manager : projectsManager.allProjects()) {
            List<JobInfo> allJobs = manager.schedulerManager().all(jobType);
            if (!allJobs.isEmpty()) {
                execute(manager, allJobs);
            }
        }
    }

    protected abstract void execute(ProjectManager projectManager, List<JobInfo> jobInfo);
}
