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

package pbouda.jeffrey.platform.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.model.job.JobInfo;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.platform.scheduler.Job;
import pbouda.jeffrey.platform.scheduler.JobContext;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptor;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;

import java.time.Duration;
import java.util.List;

/**
 * Base class for PROJECT-level jobs that operate on individual projects.
 * Unlike WorkspaceJob which uses the global scheduler, ProjectJob queries
 * project-level schedulers to find job configurations.
 */
public abstract class ProjectJob<T extends JobDescriptor<T>> implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectJob.class);

    private final WorkspacesManager workspacesManager;
    protected final JobDescriptorFactory jobDescriptorFactory;

    protected ProjectJob(
            WorkspacesManager workspacesManager,
            JobDescriptorFactory jobDescriptorFactory) {
        this.workspacesManager = workspacesManager;
        this.jobDescriptorFactory = jobDescriptorFactory;
    }

    @Override
    public void execute(JobContext context) {
        String simpleName = this.getClass().getSimpleName();

        // Iterate all workspaces (no isLive filter - runs for all projects)
        for (WorkspaceManager workspaceManager : workspacesManager.findAll()) {
            // Iterate all projects in the workspace
            for (ProjectManager projectManager : workspaceManager.projectsManager().findAll()) {
                // Query project-level scheduler for this job type
                List<JobInfo> projectJobs = projectManager.schedulerManager().all(jobType());

                for (JobInfo jobInfo : projectJobs) {
                    if (jobInfo.enabled()) {
                        T jobDescriptor = jobDescriptorFactory.create(jobInfo);

                        long start = System.nanoTime();
                        execute(projectManager, jobDescriptor, context);
                        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
                        LOG.debug("Job completed: job={} elapsed_ms={} workspace_id={} project_id={}",
                                simpleName, elapsed.toMillis(), workspaceManager.resolveInfo().id(), projectManager.info().id());
                    }
                }
            }
        }
    }

    protected abstract void execute(ProjectManager projectManager, T jobDescriptor, JobContext context);
}
