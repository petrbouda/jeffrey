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
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.common.model.WorkspaceInfo;
import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.manager.WorkspacesManager;
import pbouda.jeffrey.scheduler.WorkspaceRepository;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.scheduler.job.model.SynchronizationMode;
import pbouda.jeffrey.scheduler.job.sync.CreateOnlySynchronizationModeStrategy;
import pbouda.jeffrey.scheduler.job.sync.FullSyncSynchronizationModeStrategy;
import pbouda.jeffrey.scheduler.job.sync.SynchronizationModeStrategy;
import pbouda.jeffrey.scheduler.model.WorkspaceProject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class ProjectsSynchronizerJob extends WorkspaceJob<ProjectsSynchronizerJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectsSynchronizerJob.class);
    private static final JobType JOB_TYPE = JobType.PROJECTS_SYNCHRONIZER;

    private final HomeDirs homeDirs;
    private final ProjectsManager projectsManager;
    private final List<SynchronizationModeStrategy> synchronizationModeStrategies;

    public ProjectsSynchronizerJob(
            HomeDirs homeDirs,
            WorkspacesManager workspacesManager,
            ProjectsManager projectsManager,
            SchedulerManager schedulerManager,
            JobDescriptorFactory jobDescriptorFactory,
            Duration period) {

        super(workspacesManager, schedulerManager, jobDescriptorFactory, JOB_TYPE, period);
        this.homeDirs = homeDirs;
        this.projectsManager = projectsManager;
        this.synchronizationModeStrategies = List.of(
                new CreateOnlySynchronizationModeStrategy(projectsManager),
                new FullSyncSynchronizationModeStrategy(projectsManager));
    }

    @Override
    protected void execute(WorkspaceInfo workspaceInfo, ProjectsSynchronizerJobDescriptor jobDescriptor) {
        LOG.debug("Executing ProjectsSynchronizerJob: {}", jobDescriptor);
        Path workspacesDir = jobDescriptor.workspacesDir();
        SynchronizationMode syncMode = jobDescriptor.syncMode();

        if (Files.notExists(workspacesDir)) {
            throw new IllegalArgumentException(
                    "The workspacesDir for synchronizing projects does not exist: " + workspacesDir);
        }
        if (!Files.isDirectory(workspacesDir)) {
            throw new IllegalArgumentException("The workspacesDir is not a directory: " + workspacesDir);
        }

        processSingleWorkspace(workspaceInfo, projectsManager, jobDescriptor.templateId(), syncMode);

        LOG.info("ProjectsSynchronizer Job completed for workspacesDir: {}", jobDescriptor);
    }

    private void processSingleWorkspace(
            WorkspaceInfo workspaceInfo,
            ProjectsManager projectsManager,
            String templateId,
            SynchronizationMode syncMode) {

        SynchronizationModeStrategy synchronizationModeStrategy = synchronizationModeStrategies.stream()
                .filter(strategy -> strategy.synchronizationMode() == syncMode)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No synchronization mode strategy found for: " + syncMode));

        // Resolve the workspace path based on the workspace info
        // If the workspaceInfo does not have a path, use the default home directory's workspaces path
        Path workspacePath = resolveWorkspacePath(workspaceInfo);

        // Retrieve all projects from the remote workspace SQLite database
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(workspacePath);
        List<WorkspaceProject> workspaceProjects = workspaceRepository.allProjects();

        synchronizationModeStrategy.executeOnWorkspace(
                workspaceProjects,
                projectsManager.allProjects(workspaceInfo.id()),
                templateId);
    }

    private Path resolveWorkspacePath(WorkspaceInfo workspaceInfo) {
        if (workspaceInfo.path() == null) {
            return homeDirs.workspaces().resolve(workspaceInfo.id());
        }
        return Path.of(workspaceInfo.path());
    }
}
