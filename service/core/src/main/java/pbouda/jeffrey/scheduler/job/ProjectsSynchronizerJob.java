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
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.scheduler.job.model.SynchronizationMode;
import pbouda.jeffrey.scheduler.job.sync.CreateOnlySynchronizationModeStrategy;
import pbouda.jeffrey.scheduler.job.sync.FullSyncSynchronizationModeStrategy;
import pbouda.jeffrey.scheduler.job.sync.SynchronizationModeStrategy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class ProjectsSynchronizerJob extends GlobalJob<ProjectsSynchronizerJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectsSynchronizerJob.class);
    private static final JobType JOB_TYPE = JobType.PROJECTS_SYNCHRONIZER;

    private final ProjectsManager projectsManager;
    private final List<SynchronizationModeStrategy> synchronizationModeStrategies;

    public ProjectsSynchronizerJob(
            ProjectsManager projectsManager,
            SchedulerManager schedulerManager,
            JobDescriptorFactory jobDescriptorFactory,
            Duration period) {

        super(schedulerManager, jobDescriptorFactory, JOB_TYPE, period);
        this.projectsManager = projectsManager;
        this.synchronizationModeStrategies = List.of(
                new CreateOnlySynchronizationModeStrategy(projectsManager),
                new FullSyncSynchronizationModeStrategy(projectsManager));
    }

    @Override
    protected void execute(ProjectsSynchronizerJobDescriptor jobDescriptor) {
        LOG.debug("Executing ProjectsSynchronizerJob: {}", jobDescriptor);
        Path repositoriesDir = jobDescriptor.workspacesDir();
        SynchronizationMode syncMode = jobDescriptor.syncMode();


        if (Files.notExists(repositoriesDir)) {
            throw new IllegalArgumentException(
                    "The repositoriesDir for synchronizing projects does not exist: " + repositoriesDir);
        }
        if (!Files.isDirectory(repositoriesDir)) {
            throw new IllegalArgumentException("The repositoriesDir is not a directory: " + repositoriesDir);
        }

        // All folders in watched folder, a new project needs to be created if there is any new folder
        List<Path> currentFolders = FileSystemUtils.allDirectoriesInDirectory(repositoriesDir);

        SynchronizationModeStrategy synchronizationModeStrategy = synchronizationModeStrategies.stream()
                .filter(strategy -> strategy.synchronizationMode() == syncMode)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No synchronization mode strategy found for: " + syncMode));

        synchronizationModeStrategy.execute(currentFolders, projectsManager.allProjects(), jobDescriptor.templateId());
        LOG.info("ProjectsSynchronizer Job completed for repositoriesDir: {}", jobDescriptor);
    }
}
