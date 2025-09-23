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

import pbouda.jeffrey.common.model.job.JobInfo;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.manager.project.ProjectsManager;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptor;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;

import java.util.List;

public abstract class ProjectJob<T extends JobDescriptor<T>> implements Job {

    private final ProjectsManager projectsManager;
    private final JobDescriptorFactory jobDescriptorFactory;

    public ProjectJob(
            ProjectsManager projectsManager,
            JobDescriptorFactory jobDescriptorFactory) {

        this.projectsManager = projectsManager;
        this.jobDescriptorFactory = jobDescriptorFactory;
    }

    @Override
    public void run() {
        for (ProjectManager manager : projectsManager.findAll()) {
            List<JobInfo> allJobs = manager.schedulerManager().all(jobType());
            for (JobInfo jobInfo : allJobs) {
                if (jobInfo.enabled()) {
                    T jobDescriptor = jobDescriptorFactory.create(jobInfo);
                    execute(manager, jobDescriptor);
                }
            }
        }
    }

    protected abstract void execute(ProjectManager projectManager, T jobDescriptor);
}
