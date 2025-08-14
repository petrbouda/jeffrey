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

import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.manager.WorkspaceManager;
import pbouda.jeffrey.manager.WorkspacesManager;
import pbouda.jeffrey.repository.RemoteWorkspaceRepository;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.scheduler.model.WorkspaceProject;
import pbouda.jeffrey.workspace.WorkspaceEventConsumerType;

import java.time.Duration;
import java.util.List;

public class ProjectsSynchronizerJob extends WorkspaceJob<ProjectsSynchronizerJobDescriptor> {

    private final ProjectsManager projectsManager;
    private final Duration period;

    public ProjectsSynchronizerJob(
            WorkspacesManager workspacesManager,
            ProjectsManager projectsManager,
            SchedulerManager schedulerManager,
            JobDescriptorFactory jobDescriptorFactory,
            Duration period) {

        super(workspacesManager, schedulerManager, jobDescriptorFactory);
        this.projectsManager = projectsManager;
        this.period = period;
    }

    @Override
    protected void executeOnWorkspace(
            WorkspaceManager workspaceManager, ProjectsSynchronizerJobDescriptor jobDescriptor) {

        List<WorkspaceEvent> workspaceEvents =
                workspaceManager.remainingEvents(WorkspaceEventConsumerType.PROJECT_SYNCHRONIZER_CONSUMER);

        // Retrieve all projects from the remote workspace SQLite database
        RemoteWorkspaceRepository remoteWorkspaceRepository = workspaceManager.remoteWorkspaceRepository();
        List<WorkspaceProject> workspaceProjects = remoteWorkspaceRepository.allProjects();

    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.PROJECTS_SYNCHRONIZER;
    }
}
