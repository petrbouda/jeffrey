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
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptor;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;

import java.util.List;

public abstract class ProjectJob<T extends JobDescriptor<T>> extends WorkspaceJob<T> {

    private final JobDescriptorFactory jobDescriptorFactory;

    public ProjectJob(
            WorkspacesManager workspacesManager,
            SchedulerManager schedulerManager,
            JobDescriptorFactory jobDescriptorFactory) {
        super(workspacesManager, schedulerManager, jobDescriptorFactory);
        this.jobDescriptorFactory = jobDescriptorFactory;
    }

    @Override
    public void executeOnWorkspace(WorkspaceManager workspaceManager, T jobInfo) {
        for (ProjectManager manager : workspaceManager.projectsManager().findAll()) {
            List<JobInfo> allJobs = manager.schedulerManager().all(jobType());
            for (JobInfo job : allJobs) {
                if (job.enabled()) {
                    T jobDescriptor = jobDescriptorFactory.create(job);
                    execute(manager, jobDescriptor);
                }
            }
        }
    }

    protected abstract void execute(ProjectManager projectManager, T jobDescriptor);
}
