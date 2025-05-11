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
import pbouda.jeffrey.provider.api.model.job.JobInfo;
import pbouda.jeffrey.provider.api.model.job.JobType;

import java.time.Duration;
import java.util.List;

public abstract class ProjectJob extends Job {

    private final ProjectsManager projectsManager;

    public ProjectJob(ProjectsManager projectsManager, JobType jobType, Duration period) {
        super(jobType, period);
        this.projectsManager = projectsManager;
    }

    @Override
    public void run() {
        for (ProjectManager manager : projectsManager.allProjects()) {
            List<JobInfo> allJobs = manager.schedulerManager().all(jobType());
            for (JobInfo jobInfo : allJobs) {
                if (jobInfo.enabled()) {
                    execute(manager, jobInfo);
                }
            }
        }
    }

    protected abstract void execute(ProjectManager projectManager, JobInfo jobInfo);
}
