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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.job.JobInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.manager.WorkspaceManager;
import pbouda.jeffrey.manager.WorkspacesManager;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptor;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public abstract class WorkspaceJob<T extends JobDescriptor<T>> implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceJob.class);

    private final WorkspacesManager workspacesManager;
    private final SchedulerManager schedulerManager;
    private final JobDescriptorFactory jobDescriptorFactory;

    public WorkspaceJob(
            WorkspacesManager workspacesManager,
            SchedulerManager schedulerManager,
            JobDescriptorFactory jobDescriptorFactory) {

        this.workspacesManager = workspacesManager;
        this.schedulerManager = schedulerManager;
        this.jobDescriptorFactory = jobDescriptorFactory;
    }

    @Override
    public void run() {
        String simpleName = this.getClass().getSimpleName();
        List<JobInfo> allJobs = schedulerManager.all(jobType());
        if (allJobs.isEmpty()) {
            LOG.debug("No jobs of type {} found, skipping execution", jobType());
            return;
        }

        List<? extends WorkspaceManager> allWorkspaces = workspacesManager.allWorkspaces();

        for (JobInfo jobInfo : allJobs) {
            if (jobInfo.enabled()) {
                T jobDescriptor = jobDescriptorFactory.create(jobInfo);

                // Iterate the same job for all workspaces
                for (WorkspaceManager workspaceManager : allWorkspaces) {
                    WorkspaceInfo workspaceInfo = workspaceManager.info();
                    Optional<Path> workspacePath = workspaceManager.workspacePath();

                    if (workspacePath.isEmpty()) {
                        LOG.warn("Workspace dir does not exists, or is invalid: job={} workspace_path={}",
                                simpleName, workspacePath);
                        continue;
                    }

                    LOG.debug("Executing Job: job={} workspace={} workspace_dir={}",
                            simpleName, workspaceInfo.id(), workspacePath);
                    executeOnWorkspace(workspaceManager, jobDescriptor);
                    LOG.info("Job completed: job={} workspace={} workspace_dir={}",
                            simpleName, workspaceManager.info().id(), workspacePath);
                }
            }
        }
    }

    protected abstract void executeOnWorkspace(WorkspaceManager workspaceManager, T jobInfo);
}
